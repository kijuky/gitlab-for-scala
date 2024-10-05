package io.github.kijuky.gitlab

import com.github.difflib.UnifiedDiffUtils._
import com.github.difflib.patch.Patch
import io.github.kijuky.diff.Implicits._
import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.models._

import scala.collection.JavaConverters._

object Implicits {
  implicit class RichGitLab(gitlab: GitLabApi) {
    private implicit val implicitGitLab: GitLabApi = gitlab
    private lazy val serverUrl = gitlab.getGitLabServerUrl
    private lazy val projectPath = CIEnv.projectPath.getOrElse("")
    private lazy val projectId = CIEnv.projectId.map(_.toInt).getOrElse(0)
    private lazy val userEmail = CIEnv.userEmail.getOrElse("")
    private lazy val projectBaseUrl = s"$serverUrl/$projectPath"
    private lazy val commitId = CIEnv.commitId.getOrElse("")
    private lazy val commitBaseUrl = s"$projectBaseUrl/-/commit"
    private lazy val pipelineId = CIEnv.pipelineId.getOrElse("")
    private lazy val pipelineBaseUrl = s"$projectBaseUrl/-/pipelines"
    private lazy val mrIid = CIEnv.mrIid.getOrElse("")
    private lazy val mrBaseUrl = s"$projectBaseUrl/-/merge_requests"
    private lazy val branchBaseUrl = s"$projectBaseUrl/-/tree"

    lazy val loginUser: String = CIEnv.loginUser.getOrElse("")
    lazy val branchName: String = CIEnv.branchName.getOrElse("")
    lazy val pipelineUrl = s"$pipelineBaseUrl/$pipelineId"
    lazy val env: String = CIEnv.env.getOrElse("")
    lazy val commitUrl = s"$commitBaseUrl/$commitId"
    lazy val mrUrl = s"$mrBaseUrl/$mrIid"

    def branchBrowseUrl(branchName: String = branchName) =
      s"$branchBaseUrl/$branchName"

    private lazy val mergeRequestApi = gitlab.getMergeRequestApi
    private lazy val userApi = gitlab.getUserApi
    private lazy val discussionApi = gitlab.getDiscussionsApi

    def mergeRequest(
      mergeRequestIid: Long,
      projectPath: String = projectPath
    ): MergeRequest =
      mergeRequestApi.getMergeRequest(projectPath, mergeRequestIid)

    def updateMergeRequest(
      mergeRequestIid: Long,
      params: MergeRequestParams,
      projectPath: String = projectPath
    ): Unit =
      mergeRequestApi.updateMergeRequest(projectPath, mergeRequestIid, params)

    def user(username: String): User = userApi.getUser(username)

    def discussions(mergeRequest: MergeRequest): Seq[Discussion] =
      discussionApi
        .getMergeRequestDiscussions(mergeRequest.projectId, mergeRequest.iid)
        .asScala
        .toSeq

    def createDiscussion(mergeRequest: MergeRequest, body: String): Discussion =
      discussionApi
        .createMergeRequestDiscussion(
          mergeRequest.projectId,
          mergeRequest.iid,
          body,
          new java.util.Date(),
          null,
          null
        )

    /** マージリクエストにコメント（Discussion）を追加します。
      *
      * @param mergeRequest
      *   コメントを追加するマージリクエスト
      * @param body
      *   コメント
      * @param file
      *   コメントを追加するファイル名。名前を変更したファイルの場合は変更後のファイル名。削除したファイルの場合は削除したファイル名。
      * @param line
      *   コメントを追加する行位置。新規追加ファイル及び名前を変更したファイルの場合は変更後のファイル基準。削除したファイルの場合は削除したファイル基準。
      * @return
      *   追加されたコメント
      */
    def createDiscussion(
      mergeRequest: MergeRequest,
      body: String,
      file: String,
      line: Int
    ): Discussion = {
      val position = {
        val mrPosition =
          mergeRequestApi
            .getMergeRequestDiffs(mergeRequest.projectId, mergeRequest.iid)
            .get(0)
            .position
        val diffs =
          mergeRequestApi
            .getMergeRequestChanges(mergeRequest.projectId, mergeRequest.iid)
            .changes
        diffs
          .filter(_.getDiff.nonEmpty)
          .find { diff => file.endsWith(diff.newPath) }
          .map { diff =>
            val position =
              mrPosition
                .withNewPath(diff.newPath)
                .withOldPath(diff.oldPath)
            if (diff.isDeleted) {
              position.withOldLine(line)
            } else if (diff.isCreated) {
              position.withNewLine(line)
            } else { // include rename
              position
                .withNewLine(line)
                // 対応する古いソースコードの行数を設定する必要がある。
                // MEMO: 対応する古いソースコードの行数が存在する場合、それは今回のコミットで変更していない行であるため、コメントの出力を抑制しても良いかもしれない。
                .withOldLine(diff.patch.sourceLineNumber(line).toInteger)
            }
          }
      }

      position match {
        case Some(position) =>
          discussionApi
            .createMergeRequestDiscussion(
              mergeRequest.projectId,
              mergeRequest.iid,
              body,
              new java.util.Date(),
              null,
              position
            )
        case None =>
          // position が存在しない場合はファイル名と行数を body に追加する
          discussionApi
            .createMergeRequestDiscussion(
              mergeRequest.projectId,
              mergeRequest.iid,
              s"$body\n\n(at $file#$line)",
              new java.util.Date(),
              null,
              null
            )
      }
    }

    def modifyDiscussion(
      mergeRequest: MergeRequest,
      discussion: Discussion,
      body: String
    ): Note =
      discussionApi
        .modifyMergeRequestThreadNote(
          mergeRequest.projectId,
          mergeRequest.iid,
          discussion.id,
          discussion.notes.head.id,
          body,
          null
        )

    def deleteDiscussion(
      mergeRequest: MergeRequest,
      discussion: Discussion
    ): Unit =
      discussionApi.deleteMergeRequestDiscussionNote(
        mergeRequest.projectId,
        mergeRequest.iid,
        discussion.id,
        discussion.notes.head.id
      )
  }

  implicit class RichMergeRequest(mergeRequest: MergeRequest)(implicit
    gitlab: GitLabApi
  ) {
    private def toUserIds(
      usernames: Seq[String]
    ): java.util.List[java.lang.Long] =
      usernames.map(gitlab.user(_).getId).asJava
    private def update(params: MergeRequestParams): Unit =
      gitlab.updateMergeRequest(iid, params)

    def author: Author = mergeRequest.getAuthor
    def authorUsername: String = author.getUsername
    def assignees: Seq[Assignee] = mergeRequest.getAssignees.asScala.toSeq
    def assigneeUsernames: Seq[String] = assignees.map(_.getUsername)
    def assigneeUsernames_=(usernames: Seq[String]): Unit =
      update(new MergeRequestParams().withAssigneeIds(toUserIds(usernames)))
    def changes: Seq[Diff] = mergeRequest.getChanges.asScala.toSeq
    def description: String = mergeRequest.getDescription
    def description_=(description: String): Unit =
      update(new MergeRequestParams().withDescription(description))
    def diffRef: DiffRef = mergeRequest.getDiffRefs
    def iid: Long = mergeRequest.getIid
    def projectId: Long = mergeRequest.getProjectId
    def reviewers: Seq[Reviewer] = mergeRequest.getReviewers.asScala.toSeq
    def reviewerUsernames: Seq[String] = reviewers.map(_.getUsername)
    def reviewerUsernames_=(usernames: Seq[String]): Unit =
      update(new MergeRequestParams().withReviewerIds(toUserIds(usernames)))
    def squash: Boolean = mergeRequest.getSquash
    def squash_=(squash: Boolean): Unit =
      update(new MergeRequestParams().withSquash(squash))
    def title: String = mergeRequest.getTitle
    def webUrl: String = mergeRequest.getWebUrl

    /** Danger っぽく、GitLab のマージリクエストにコメントを追加するための機能を提供します。
      *
      * 一般的な Danger との違いは次の通りです。
      *   - コメントを消す場合には resolved メソッドを呼ぶ必要があります。
      *   - 各コメントは prefix によって区別されます。
      *     - prefix はコメントに出力されます。
      *   - マージリクエストに対するマージコメントはマージされません。
      */
    def createDanger(botUsername: String) = new Danger(botUsername)

    class Danger(botUsername: String) {
      private def discussion(prefix: String): Option[Discussion] =
        gitlab.discussions(mergeRequest).find { discussion =>
          discussion.notes.headOption.exists { note =>
            note.isUnresolved &&
            note.authorUsername == botUsername &&
            note.body.startsWith(prefix)
          }
        }

      private def discussion(
        prefix: String,
        file: String,
        line: Int
      ): Option[Discussion] =
        gitlab.discussions(mergeRequest).find { discussion =>
          discussion.notes.headOption.exists { note =>
            note.isUnresolved &&
            note.authorUsername == botUsername &&
            note.body.startsWith(prefix) &&
            note.position.exists { position =>
              file.endsWith(position.newPath) &&
              position.newLine == line
            }
          }
        }

      /** prefix で始まるコメント (Discussion) を削除します。
        * @param prefix
        *   コメントの先頭部分
        */
      def resolved(prefix: String): Unit =
        discussion(prefix).foreach(gitlab.deleteDiscussion(mergeRequest, _))

      /** コメント (Discussion) を追加します。
        * @param body
        *   コメントの本文
        */
      def info(body: String): Unit = {
        info(body, "")
      }

      /** コメント (Discussion) を追加します。
        * @param prefix
        *   コメントの先頭部分
        * @param text
        *   コメントの本文
        */
      def info(prefix: String, text: String): Unit = {
        val body = prefix + text
        discussion(prefix) match {
          case Some(discussion) =>
            gitlab.modifyDiscussion(mergeRequest, discussion, body)
          case None =>
            gitlab.createDiscussion(mergeRequest, body)
        }
      }

      /** コメント (Discussion) を追加します。
        * @param prefix
        *   コメントの先頭部分
        * @param text
        *   コメントの本文
        * @param file
        *   コメントを追加するファイル
        * @param line
        *   コメントを追加するファイルの行番号（新しい差分基準）
        */
      def info(prefix: String, text: String, file: String, line: Int): Unit = {
        val body = prefix + text
        discussion(prefix, file, line) match {
          case Some(discussion) =>
            gitlab.modifyDiscussion(mergeRequest, discussion, body)
          case None =>
            gitlab.createDiscussion(mergeRequest, body, file, line)
        }
      }

      /** コメント (Discussion) を追加し、CIを失敗させます。
        * @param prefix
        *   コメントの先頭部分
        * @param text
        *   コメントの本文
        */
      def failure(prefix: String, text: String): Unit = {
        info(prefix, text)
        sys.error(prefix + text)
      }
    }
  }

  implicit class RichMergeRequestDiff(mergeRequestDiff: MergeRequestDiff) {
    def baseCommitSha: String = mergeRequestDiff.getBaseCommitSha
    def headCommitSha: String = mergeRequestDiff.getHeadCommitSha
    def startCommitSha: String = mergeRequestDiff.getStartCommitSha
    def position: Position =
      new Position()
        .withPositionType(Position.PositionType.TEXT)
        .withBaseSha(baseCommitSha)
        .withHeadSha(headCommitSha)
        .withStartSha(startCommitSha)
  }

  implicit class RichDiff(diff: Diff) {
    def newPath: String = diff.getNewPath
    def oldPath: String = diff.getOldPath
    def isDeleted: Boolean = diff.getDeletedFile
    def isCreated: Boolean = diff.getNewFile
    private def unifiedDiff = s"+++\n${diff.getDiff.replace(" @@ ", " @@\n")}"
    def patch: Patch[String] =
      parseUnifiedDiff(java.util.List.of(unifiedDiff.split("\n"): _*))
  }

  implicit class RichDiffRef(diffRef: DiffRef) {
    def startSha: String = diffRef.getStartSha
  }

  implicit class RichDiscussion(discussion: Discussion) {
    def id: String = discussion.getId
    def notes: Seq[Note] = discussion.getNotes.asScala.toSeq
  }

  implicit class RichNote(note: Note) {
    def author: Author = note.getAuthor
    def authorUsername: String = author.getUsername
    def body: String = note.getBody
    def id: Long = note.getId
    def position: Option[Position] = Option(note.getPosition)
    def isResolved: Boolean = note.getResolved
    def isUnresolved: Boolean = !isResolved
  }

  implicit class RichPosition(position: Position) {
    def newPath: String = position.getNewPath
    def newLine: Int = position.getNewLine
  }
}
