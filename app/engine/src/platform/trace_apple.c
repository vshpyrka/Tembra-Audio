/* os_signpost binding of the tracing facade (feature 007); compiled on Apple platforms. */
#include "tembra_trace.h"

#if defined(TEMBRA_TRACING) && TEMBRA_TRACING
#include <os/log.h>
#include <os/signpost.h>
#include <string.h>

static os_log_t tembra_trace_log(const char* category) {
    /* One log handle per category; the categories are the three fixed strings of the contract. */
    static os_log_t ui = NULL;
    static os_log_t engine = NULL;
    static os_log_t counter = NULL;
    if (strcmp(category, "ui") == 0) {
        if (ui == NULL) ui = os_log_create("audio.tembra", "ui");
        return ui;
    }
    if (strcmp(category, "counter") == 0) {
        if (counter == NULL) counter = os_log_create("audio.tembra", "counter");
        return counter;
    }
    if (engine == NULL) engine = os_log_create("audio.tembra", "engine");
    return engine;
}

#include "tembra_trace_names.h"

/*
 * os_signpost takes its name as a string literal, so a runtime name cannot be passed through (the reason Compose's
 * trace() emits one fixed name); the catalogue is fixed, so each function dispatches through the generated X-macro
 * to the literal of the matching entry. A name outside the catalogue is recorded under "tembra.other" with the
 * name in the message. One outstanding interval per name per thread is the contract; the id derives from the
 * entry's literal so the end matches its begin.
 */
#define TEMBRA_SIGNPOST_ID(log, lit) os_signpost_id_make_with_pointer((log), (const void*)(lit))

void tembra_trace_begin(const char* category, const char* name) {
    os_log_t log = tembra_trace_log(category);
#define TEMBRA_X(id, lit) if (strcmp(name, lit) == 0) { os_signpost_interval_begin(log, TEMBRA_SIGNPOST_ID(log, lit), lit); return; }
    TEMBRA_TRACE_FOR_EACH(TEMBRA_X)
#undef TEMBRA_X
    os_signpost_interval_begin(log, os_signpost_id_make_with_pointer(log, (const void*)name), "tembra.other", "%{public}s", name);
}

void tembra_trace_end(const char* category, const char* name) {
    os_log_t log = tembra_trace_log(category);
#define TEMBRA_X(id, lit) if (strcmp(name, lit) == 0) { os_signpost_interval_end(log, TEMBRA_SIGNPOST_ID(log, lit), lit); return; }
    TEMBRA_TRACE_FOR_EACH(TEMBRA_X)
#undef TEMBRA_X
    os_signpost_interval_end(log, os_signpost_id_make_with_pointer(log, (const void*)name), "tembra.other", "%{public}s", name);
}

void tembra_trace_animation_begin(const char* category, const char* name) {
    os_log_t log = tembra_trace_log(category);
#define TEMBRA_X(id, lit) if (strcmp(name, lit) == 0) { os_signpost_animation_interval_begin(log, TEMBRA_SIGNPOST_ID(log, lit), lit); return; }
    TEMBRA_TRACE_FOR_EACH(TEMBRA_X)
#undef TEMBRA_X
    os_signpost_animation_interval_begin(log, os_signpost_id_make_with_pointer(log, (const void*)name), "tembra.other", "%{public}s", name);
}

void tembra_trace_animation_end(const char* category, const char* name) {
    tembra_trace_end(category, name);
}

void tembra_trace_counter(const char* name, long long value) {
    os_log_t log = tembra_trace_log("counter");
#define TEMBRA_X(id, lit) if (strcmp(name, lit) == 0) { os_signpost_event_emit(log, OS_SIGNPOST_ID_EXCLUSIVE, lit, "%lld", value); return; }
    TEMBRA_TRACE_FOR_EACH(TEMBRA_X)
#undef TEMBRA_X
    os_signpost_event_emit(log, OS_SIGNPOST_ID_EXCLUSIVE, "tembra.other", "%{public}s=%lld", name, value);
}

int tembra_trace_enabled(void) { return 1; }

#else

void tembra_trace_begin(const char* category, const char* name) { (void)category; (void)name; }
void tembra_trace_end(const char* category, const char* name) { (void)category; (void)name; }
void tembra_trace_animation_begin(const char* category, const char* name) { (void)category; (void)name; }
void tembra_trace_animation_end(const char* category, const char* name) { (void)category; (void)name; }
void tembra_trace_counter(const char* name, long long value) { (void)name; (void)value; }
int tembra_trace_enabled(void) { return 0; }

#endif
