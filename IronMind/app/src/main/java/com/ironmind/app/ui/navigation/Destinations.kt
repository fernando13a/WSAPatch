package com.ironmind.app.ui.navigation

/** Central definition of navigation routes and their arguments. */
object Destinations {

    const val DASHBOARD = "dashboard"

    const val ARG_SESSION_ID = "sessionId"
    const val ARG_ROUTINE_ID = "routineId"
    const val ARG_EXERCISE_ID = "exerciseId"
    const val ARG_AUTOSTART = "autostart"

    const val SESSION_ROUTE = "session?$ARG_SESSION_ID={$ARG_SESSION_ID}&$ARG_ROUTINE_ID={$ARG_ROUTINE_ID}"
    const val PROGRESS_ROUTE = "progress?$ARG_EXERCISE_ID={$ARG_EXERCISE_ID}"
    const val ROUTINE_EDIT_ROUTE = "routineEdit?$ARG_ROUTINE_ID={$ARG_ROUTINE_ID}"
    const val MODEL_ROUTE = "model?$ARG_AUTOSTART={$ARG_AUTOSTART}"
    const val BACKUP_ROUTE = "backup"
    const val EXERCISE_DETAIL_ROUTE = "exerciseDetail?$ARG_EXERCISE_ID={$ARG_EXERCISE_ID}"

    /** Builds a session route. sessionId=0 means "start a new session" (optionally from routineId). */
    fun session(sessionId: Long = 0L, routineId: Long = 0L): String =
        "session?$ARG_SESSION_ID=$sessionId&$ARG_ROUTINE_ID=$routineId"

    /** Builds a progress route. exerciseId=0 lets the screen pick the most recently trained exercise. */
    fun progress(exerciseId: Long = 0L): String =
        "progress?$ARG_EXERCISE_ID=$exerciseId"

    /** Builds a routine-edit route. routineId=0 means "create a new routine". */
    fun routineEdit(routineId: Long = 0L): String =
        "routineEdit?$ARG_ROUTINE_ID=$routineId"

    /** Builds an exercise-detail route (equipment, how-to guide, reference image). */
    fun exerciseDetail(exerciseId: Long): String =
        "exerciseDetail?$ARG_EXERCISE_ID=$exerciseId"

    /** Builds the model route. autostart=true begins the download from the configured URL on entry. */
    fun model(autostart: Boolean = false): String =
        "model?$ARG_AUTOSTART=$autostart"
}
