#!/usr/bin/env bash
# push-chunks.sh - push a branch to GitHub in small chunks.
#
# Why: some networks this Mac uses drop git packs above a few MB, so a plain
# `git push` of a branch carrying screenshots or other large blobs stalls. This
# script sends the blobs the remote lacks first, in groups of at most N MB, each
# group as a throwaway commit pushed to a temporary ref, and then pushes the real
# branch, where only trees and commits remain.
#
# Usage:
#   tools/push-chunks.sh [--dry-run] [--chunk-mb N] [--remote origin] <branch>
#
#   --dry-run     print the missing blob count and size, the chunk plan and the
#                 push commands; push nothing and create no refs (the temporary
#                 commits are still built locally, as unreachable objects)
#   --chunk-mb N  chunk size limit in MB (default 2); a blob larger than the
#                 limit is a chunk of its own (single ~6 MB blobs pushed fine)
#   --remote R    remote name (default origin)
#
# Remote tip (what the remote already has): <remote>/<branch> when it exists,
# else <remote>/main. No fetch is done; run `git fetch <remote>` first if the
# tracking refs are stale.
#
# How: `git rev-list --objects <remote-tip>..<branch>` piped into
# `git cat-file --batch-check='%(objectname) %(objecttype) %(objectsize) %(rest)'`
# (the %(rest) is required, otherwise "<sha> <path>" lines read as missing),
# keep the blob lines, group them greedily, and for each chunk build a commit
# with a scratch index (read-tree parent, update-index --add --cacheinfo
# chunk/<blob>, write-tree, commit-tree; the first parent is the remote tip,
# then the previous chunk commit) pushed with -f to refs/heads/tmp-push-chunks
# (-f lets a rerun restart the chain). Finally `git push -u <remote> <branch>`
# and `git push <remote> --delete tmp-push-chunks`.
#
# Results so far: 2026-10-16 156 MB as 96 chunks; 2026-10-27 88 MB as 40 chunks.
#
# Shell traps: use ${sha}:refs/heads/... with braces, and never name a variable
# `path` in a `read` loop (it clobbers PATH in zsh).

set -euo pipefail

TMP_REF="tmp-push-chunks"
dry_run=0
chunk_mb=2
remote="origin"
branch=""

usage() { sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//' >&2; exit "${1:-2}"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) dry_run=1 ;;
    --chunk-mb) shift; chunk_mb="${1:?--chunk-mb needs a value}" ;;
    --remote) shift; remote="${1:?--remote needs a value}" ;;
    -h|--help) usage 0 ;;
    -*) echo "unknown option: $1" >&2; usage ;;
    *) [ -z "$branch" ] || { echo "only one branch" >&2; usage; }; branch="$1" ;;
  esac
  shift
done
[ -n "$branch" ] || usage
case "$chunk_mb" in ''|*[!0-9]*|0) echo "--chunk-mb must be a positive integer" >&2; exit 2 ;; esac
limit=$((chunk_mb * 1024 * 1024))

local_tip=$(git rev-parse --verify "refs/heads/${branch}^{commit}")
if remote_tip=$(git rev-parse --verify --quiet "refs/remotes/${remote}/${branch}^{commit}"); then
  base_name="${remote}/${branch}"
else
  remote_tip=$(git rev-parse --verify "refs/remotes/${remote}/main^{commit}")
  base_name="${remote}/main"
fi
echo "branch ${branch} at ${local_tip:0:10}; remote tip ${base_name} at ${remote_tip:0:10}"

work=$(mktemp -d "${TMPDIR:-/tmp}/push-chunks.XXXXXX")
trap 'rm -rf "$work"' EXIT
idx="$work/index"
blobs="$work/blobs"

git rev-list --objects "${remote_tip}..${local_tip}" \
  | git cat-file --batch-check='%(objectname) %(objecttype) %(objectsize) %(rest)' \
  | awk '$2 == "blob" { print $1, $3 }' | sort -u > "$blobs"

count=$(wc -l < "$blobs" | tr -d ' ')
total=$(awk '{ s += $2 } END { print s + 0 }' "$blobs")
mb() { awk -v b="$1" 'BEGIN { printf "%.2f", b / 1048576 }'; }
echo "missing blobs: ${count}, total $(mb "$total") MB"

# Greedy grouping: write chunk files $work/chunk.NNN (blob shas) and a plan.
awk -v limit="$limit" -v dir="$work" '
  function flush() { if (n > 0) { printf "%d %d\n", n, size >> (dir "/plan"); close(dir "/plan") } }
  BEGIN { c = 0; n = 0; size = 0 }
  {
    if (n > 0 && size + $2 > limit) { flush(); c++; n = 0; size = 0 }
    f = sprintf("%s/chunk.%04d", dir, c + 1)
    print $1 >> f
    n++; size += $2
  }
  END { flush() }
' "$blobs"
chunks=0
[ -f "$work/plan" ] && chunks=$(wc -l < "$work/plan" | tr -d ' ')
echo "chunk plan: ${chunks} chunk(s) of at most ${chunk_mb} MB (a larger single blob is its own chunk)"
if [ "$chunks" -gt 0 ]; then
  i=0
  while read -r n bytes; do
    i=$((i + 1))
    printf '  chunk %d: %d blob(s), %s MB\n' "$i" "$n" "$(mb "$bytes")"
  done < "$work/plan"
fi

parent="$remote_tip"
i=0
while [ "$i" -lt "$chunks" ]; do
  i=$((i + 1))
  f=$(printf '%s/chunk.%04d' "$work" "$i")
  rm -f "$idx"
  GIT_INDEX_FILE="$idx" git read-tree "$parent"
  while read -r sha; do
    GIT_INDEX_FILE="$idx" git update-index --add --cacheinfo "100644,${sha},chunk/${sha}"
  done < "$f"
  tree=$(GIT_INDEX_FILE="$idx" git write-tree)
  commit=$(git commit-tree "$tree" -p "$parent" -m "push chunk ${i}")
  parent="$commit"
  cmd="git push -f ${remote} ${commit}:refs/heads/${TMP_REF}"
  if [ "$dry_run" -eq 1 ]; then
    echo "[${i}/${chunks}] built ${commit:0:10}; would run: ${cmd}"
  else
    echo "[${i}/${chunks}] pushing ${commit:0:10}"
    $cmd
  fi
done

if [ "$dry_run" -eq 1 ]; then
  echo "would run: git push -u ${remote} ${branch}"
  [ "$chunks" -gt 0 ] && echo "would run: git push ${remote} --delete ${TMP_REF}"
  echo "dry run: nothing pushed, no refs created"
  exit 0
fi

echo "pushing ${branch}"
git push -u "$remote" "$branch"
if [ "$chunks" -gt 0 ]; then
  git push "$remote" --delete "$TMP_REF"
fi
echo "done"
