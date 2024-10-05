package io.github.kijuky.gitlab

object CIEnv {
  lazy val env: Option[String] =
    sys.env.get("ENV")
  lazy val loginUser: Option[String] =
    sys.env.get("GITLAB_USER_LOGIN")
  lazy val projectPath: Option[String] =
    sys.env.get("CI_PROJECT_PATH")
  lazy val projectId: Option[String] =
    sys.env.get("CI_PROJECT_ID")
  lazy val branchName: Option[String] =
    sys.env.get("CI_COMMIT_REF_NAME")
  lazy val userEmail: Option[String] =
    sys.env.get("GITLAB_USER_EMAIL")
  lazy val commitId: Option[String] =
    sys.env.get("CI_COMMIT_SHA")
  lazy val pipelineId: Option[String] =
    sys.env.get("CI_PIPELINE_ID")
  lazy val jobId: Option[String] =
    sys.env.get("CI_JOB_ID")
  lazy val mrIid: Option[String] =
    sys.env.get("CI_MERGE_REQUEST_IID")
  lazy val mrDiffId: Option[String] =
    sys.env.get("CI_MERGE_REQUEST_DIFF_ID")
  lazy val mrDiffBaseSha: Option[String] =
    sys.env.get("CI_MERGE_REQUEST_DIFF_BASE_SHA")
}
