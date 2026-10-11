package dev.therealashik.jules

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val name: String = "", val id: String = "", val prompt: String = "", val title: String = "",
    val state: String = "STATE_UNSPECIFIED", val url: String = "", val sourceContext: SourceContext? = null,
    val automationMode: String = "AUTOMATION_MODE_UNSPECIFIED", val outputs: List<SessionOutput> = emptyList(),
    val createTime: String = "", val updateTime: String = ""
)
@Serializable data class GitHubBranch(val displayName: String = "")
@Serializable data class GitHubRepo(
    val owner: String = "", val repo: String = "", val isPrivate: Boolean = false,
    val defaultBranch: GitHubBranch? = null, val branches: List<GitHubBranch> = emptyList()
)
@Serializable data class GitHubRepoContext(val startingBranch: String)
@Serializable data class SourceContext(val source: String, val githubRepoContext: GitHubRepoContext? = null)
@Serializable data class Source(val name: String = "", val id: String = "", val githubRepo: GitHubRepo? = null)
@Serializable data class PullRequest(val url: String = "", val title: String = "", val description: String = "")
@Serializable data class SessionOutput(val pullRequest: PullRequest? = null)
@Serializable data class PlanStep(val id: String = "", val index: Int = 0, val title: String = "", val description: String = "")
@Serializable data class Plan(val id: String = "", val steps: List<PlanStep> = emptyList(), val createTime: String = "")
@Serializable data class PlanGenerated(val plan: Plan? = null)
@Serializable data class PlanApproved(val planId: String = "")
@Serializable data class UserMessaged(val userMessage: String = "")
@Serializable data class AgentMessaged(val agentMessage: String = "")
@Serializable data class ProgressUpdated(val title: String = "", val description: String = "")
@Serializable data class SessionCompleted(val detail: String = "")
@Serializable data class SessionFailed(val reason: String = "")
@Serializable data class GitPatch(val baseCommitId: String = "", val unidiffPatch: String = "", val suggestedCommitMessage: String = "")
@Serializable data class ChangeSet(val source: String = "", val gitPatch: GitPatch? = null)
@Serializable data class BashOutput(val command: String = "", val output: String = "", val exitCode: Int = 0)
@Serializable data class Media(val mimeType: String = "", val data: String = "")
@Serializable data class Artifact(val changeSet: ChangeSet? = null, val bashOutput: BashOutput? = null, val media: Media? = null)
@Serializable data class Activity(
    val name: String = "", val id: String = "", val originator: String = "", val description: String = "",
    val createTime: String = "", val artifacts: List<Artifact> = emptyList(),
    val planGenerated: PlanGenerated? = null, val planApproved: PlanApproved? = null,
    val userMessaged: UserMessaged? = null, val agentMessaged: AgentMessaged? = null,
    val progressUpdated: ProgressUpdated? = null, val sessionCompleted: SessionCompleted? = null,
    val sessionFailed: SessionFailed? = null
)
@Serializable data class CreateSessionRequest(
    val prompt: String, val title: String? = null, val sourceContext: SourceContext? = null,
    val requirePlanApproval: Boolean? = null, val automationMode: String? = null
)
@Serializable data class SendMessageRequest(val prompt: String)
@Serializable data class ListSessionsResponse(val sessions: List<Session> = emptyList(), val nextPageToken: String? = null)
@Serializable data class ListActivitiesResponse(val activities: List<Activity> = emptyList(), val nextPageToken: String? = null)
@Serializable data class ListSourcesResponse(val sources: List<Source> = emptyList(), val nextPageToken: String? = null)
@Serializable data class SavedPrompt(val id: String, val title: String, val prompt: String)
