package org.openedx.discussion.data.repository

import org.openedx.core.data.model.BlocksCompletionBody
import org.openedx.core.data.storage.CorePreferences
import org.openedx.discussion.data.api.DiscussionApi
import org.openedx.discussion.data.model.request.*
import org.openedx.discussion.domain.model.CommentsData
import org.openedx.discussion.domain.model.ThreadsData
import org.openedx.discussion.domain.model.Topic

class DiscussionRepository(
    private val api: DiscussionApi,
    private val preferencesManager: CorePreferences
    ) {

    private val topics = mutableListOf<Topic>()
    private var currentCourseId = ""

    suspend fun getCourseTopics(courseId: String): List<Topic> {
        val topicsData = api.getCourseTopics(courseId).mapToDomain()
        currentCourseId = courseId
        topics.clear()
        topics.addAll(topicsData.nonCoursewareTopics)
        topicsData.coursewareTopics.forEach {
            topics.add(it)
            if (it.children.isNotEmpty()) {
                topics.addAll(it.children)
            }
        }
        return topics.toList()
    }

    fun getCachedTopics(courseId: String) =
        if (courseId == currentCourseId) topics.toList() else emptyList()

    suspend fun getCourseThreads(
        courseId: String,
        following: Boolean?,
        topicId: String?,
        orderBy: String,
        view: String?,
        page: Int
    ): ThreadsData {
        return api.getCourseThreads(courseId, following, topicId, orderBy, view, page).mapToDomain()
    }

    suspend fun searchThread(
        courseId: String,
        query: String,
        page: Int
    ): ThreadsData {
        return api.searchThreads(courseId, query, page).mapToDomain()
    }

    suspend fun getThreadComments(
        threadId: String,
        page: Int
    ): CommentsData {
        return api.getThreadComments(threadId, page).mapToDomain()
    }

    suspend fun getThreadQuestionComments(
        threadId: String,
        endorsed: Boolean,
        page: Int
    ): CommentsData {
        return api.getThreadQuestionComments(threadId, page, endorsed).mapToDomain()
    }

    suspend fun setThreadRead(
        threadId: String
    ) = api.setThreadRead(threadId, ReadBody(true)).mapToDomain()

    suspend fun setThreadVoted(
        threadId: String,
        isVoted: Boolean
    ) = api.setThreadVoted(threadId, VoteBody(isVoted)).mapToDomain()

    suspend fun setThreadFlagged(
        threadId: String,
        abuseFlagged: Boolean
    ) = api.setThreadFlagged(threadId, ReportBody(abuseFlagged)).mapToDomain()

    suspend fun setThreadFollowed(
        threadId: String,
        following: Boolean
    ) = api.setThreadFollowed(threadId, FollowBody(following)).mapToDomain()

    suspend fun setCommentVoted(
        commentId: String,
        isVoted: Boolean
    ) = api.setCommentVoted(commentId, VoteBody(isVoted)).mapToDomain()

    suspend fun setCommentFlagged(
        commentId: String,
        abuseFlagged: Boolean
    ) = api.setCommentFlagged(commentId, ReportBody(abuseFlagged)).mapToDomain()

    suspend fun getCommentsResponses(
        commentId: String,
        page: Int
    ) = api.getCommentsResponses(commentId, page).mapToDomain()

    suspend fun createComment(
        threadId: String,
        rawBody: String,
        parentId: String?
    ) =
        api.createComment(CommentBody(threadId, rawBody, parentId)).mapToDomain()


    suspend fun createThread(
        topicId: String,
        courseId: String,
        type: String,
        title: String,
        rawBody: String,
        follow: Boolean
    ) = api.createThread(ThreadBody(type, topicId, courseId, title, rawBody, follow)).mapToDomain()

    suspend fun markBlocksCompletion(courseId: String, blocksId: List<String>) {
        val username = preferencesManager.user?.username ?: ""
        val blocksCompletionBody = BlocksCompletionBody(
            username,
            courseId,
            blocksId.associateWith { "1" }.toMap()
        )
        return api.markBlocksCompletion(blocksCompletionBody)
    }

}