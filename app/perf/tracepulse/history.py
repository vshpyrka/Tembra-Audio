"""The `perf-history` branch: publish round records, sync them back.

One file per round at `records/<device_id>/<round_id>.json` on an orphan branch
of this repository, worked on through a git worktree (`app/perf/.history`,
git-ignored). `publish` writes, commits (`perf: <device_id> <round_id>`) and
pushes; a rejected push is rebased (records are distinct files, so this never
conflicts) and retried; a push that still fails parks the record under
`data/unpublished/` for `publish_pending` (exit status 5 at the CLI). `sync`
fast-forwards the worktree and returns every record. The branch is append-only:
never rebase or rewrite it.
"""

import glob
import os
import shutil
import subprocess
from typing import List, Optional

from .records import RoundRecord, record_path

_PUSH_ATTEMPTS = 3


class PublishError(Exception):
    """The record is stored locally but could not reach the history branch."""


class HistoryError(Exception):
    """The worktree or the branch could not be prepared."""


class History:
    def __init__(self, repo_root: str, worktree: str, branch: str, remote: str, data_dir: str):
        self.repo_root = os.path.abspath(repo_root)
        self.worktree = worktree if os.path.isabs(worktree) else os.path.join(self.repo_root, worktree)
        self.branch = branch
        self.remote = remote
        self.data_dir = os.path.abspath(data_dir)

    # -- git plumbing ----------------------------------------------------

    def _git(self, *args: str, cwd: Optional[str] = None, check: bool = True) -> subprocess.CompletedProcess:
        proc = subprocess.run(["git", *args], cwd=cwd or self.repo_root, capture_output=True, text=True)
        if check and proc.returncode != 0:
            raise HistoryError(f"git {' '.join(args)} failed (rc={proc.returncode}): {proc.stderr.strip()}")
        return proc

    def _ref_exists(self, ref: str) -> bool:
        return self._git("show-ref", "--verify", "--quiet", ref, check=False).returncode == 0

    def _remote_ref(self) -> str:
        return f"refs/remotes/{self.remote}/{self.branch}"

    def _fetch(self) -> bool:
        """Fetch the branch; False when the remote is unreachable or lacks it."""
        proc = self._git("fetch", "--quiet", self.remote, f"+refs/heads/{self.branch}:{self._remote_ref()}",
                         check=False)
        return proc.returncode == 0

    def _worktree_registered(self) -> bool:
        listing = self._git("worktree", "list", "--porcelain").stdout
        wanted = os.path.realpath(self.worktree)
        for line in listing.splitlines():
            if line.startswith("worktree ") and os.path.realpath(line[len("worktree "):]) == wanted:
                return True
        return False

    def ensure_worktree(self) -> str:
        """Create the worktree on the branch (orphan when nowhere yet). Idempotent."""
        if self._worktree_registered():
            return self.worktree
        if os.path.exists(self.worktree):
            raise HistoryError(f"{self.worktree} exists but is not a registered worktree of {self.repo_root}")
        self._fetch()
        local = f"refs/heads/{self.branch}"
        if self._ref_exists(local):
            self._git("worktree", "add", "--quiet", self.worktree, self.branch)
        elif self._ref_exists(self._remote_ref()):
            self._git("worktree", "add", "--quiet", "--track", "-b", self.branch, self.worktree,
                      f"{self.remote}/{self.branch}")
        else:
            empty_tree = self._git("hash-object", "-t", "tree", os.devnull).stdout.strip()
            root = self._git("commit-tree", empty_tree, "-m",
                             "perf-history: round records of the performance pipeline (feature 007)",
                             ).stdout.strip()
            self._git("branch", self.branch, root)
            self._git("worktree", "add", "--quiet", self.worktree, self.branch)
        return self.worktree

    # -- publish ----------------------------------------------------------

    def _unpublished_path(self, record: RoundRecord) -> str:
        return os.path.join(self.data_dir, "unpublished", record.device.device_id, f"{record.round_id}.json")

    def _park(self, record: RoundRecord) -> str:
        path = self._unpublished_path(record)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as fh:
            fh.write(record.to_json())
        return path

    def _push(self) -> Optional[str]:
        """Push the branch; on rejection rebase onto the remote and retry. Returns
        the error text when every attempt failed."""
        error = None
        for _ in range(_PUSH_ATTEMPTS):
            proc = self._git("push", "--quiet", self.remote, f"HEAD:refs/heads/{self.branch}",
                             cwd=self.worktree, check=False)
            if proc.returncode == 0:
                return None
            error = proc.stderr.strip()
            if not self._fetch():
                return error
            rebase = self._git("rebase", "--quiet", self._remote_ref(), cwd=self.worktree, check=False)
            if rebase.returncode != 0:
                self._git("rebase", "--abort", cwd=self.worktree, check=False)
                return f"{error}\nrebase onto {self.remote}/{self.branch} failed: {rebase.stderr.strip()}"
        return error

    def publish(self, record: RoundRecord, _parked: bool = False) -> str:
        """Write, validate, commit and push one record. Returns its branch path.
        A record whose file already exists on the branch is refused (append-only)."""
        RoundRecord.validate(record.to_dict())
        self.ensure_worktree()
        rel = record_path(record.device.device_id, record.round_id)
        target = os.path.join(self.worktree, rel)
        if os.path.exists(target):
            raise PublishError(f"{rel} already exists on {self.branch}; records are never rewritten")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        with open(target, "w") as fh:
            fh.write(record.to_json())
        self._git("add", "--", rel, cwd=self.worktree)
        self._git("-c", "user.name=tracepulse", "-c", "user.email=tracepulse@tembra.audio",
                  "commit", "--quiet", "-m", f"perf: {record.device.device_id} {record.round_id}",
                  cwd=self.worktree)
        error = self._push()
        if error is not None:
            if not _parked:
                self._park(record)
            raise PublishError(f"push of {rel} to {self.remote}/{self.branch} failed; record kept under "
                               f"{os.path.join(self.data_dir, 'unpublished')} (run `tracepulse publish` to retry): "
                               f"{error}")
        if _parked:
            os.remove(self._unpublished_path(record))
        return rel

    def pending(self) -> List[RoundRecord]:
        """Records parked by a failed push, oldest first."""
        pattern = os.path.join(self.data_dir, "unpublished", "*", "*.json")
        return [RoundRecord.load(path) for path in sorted(glob.glob(pattern))]

    def publish_pending(self) -> List[str]:
        """Retry every parked record; stops at the first failure (raises)."""
        published = []
        for record in self.pending():
            rel = record_path(record.device.device_id, record.round_id)
            if os.path.exists(os.path.join(self.ensure_worktree(), rel)):
                # Committed earlier but the push failed: push the existing commit.
                error = self._push()
                if error is not None:
                    raise PublishError(f"push of {rel} failed again: {error}")
                os.remove(self._unpublished_path(record))
            else:
                self.publish(record, _parked=True)
            published.append(rel)
        return published

    # -- sync -------------------------------------------------------------

    def sync(self) -> List[RoundRecord]:
        """Fast-forward the worktree to the remote branch and return every record."""
        if not self._fetch() and not self._ref_exists(f"refs/heads/{self.branch}"):
            return []
        self.ensure_worktree()
        if self._ref_exists(self._remote_ref()):
            merge = self._git("merge", "--quiet", "--ff-only", self._remote_ref(), cwd=self.worktree, check=False)
            if merge.returncode != 0:
                raise HistoryError(f"{self.branch} diverged from {self.remote}: {merge.stderr.strip()}")
        return self.records()

    def records(self) -> List[RoundRecord]:
        """Every record in the worktree, sorted by path (device id, then round id)."""
        if not os.path.isdir(self.worktree):
            return []
        pattern = os.path.join(self.worktree, "records", "*", "*.json")
        return [RoundRecord.load(path) for path in sorted(glob.glob(pattern))]

    def remove_worktree(self) -> None:
        """Drop the worktree registration and directory (never the branch)."""
        if self._worktree_registered():
            self._git("worktree", "remove", "--force", self.worktree)
        elif os.path.isdir(self.worktree):
            shutil.rmtree(self.worktree)
