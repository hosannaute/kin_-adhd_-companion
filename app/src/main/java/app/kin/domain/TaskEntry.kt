package app.kin.domain

enum class TaskEntryAction { START, OPTIONS }
data class TaskEntry(val taskId: Long, val action: TaskEntryAction)
