package teammates.ui.webapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;

import teammates.common.datatransfer.FeedbackSessionLogEntry;
import teammates.common.datatransfer.attributes.CourseAttributes;
import teammates.common.datatransfer.attributes.FeedbackSessionAttributes;
import teammates.common.datatransfer.attributes.InstructorAttributes;
import teammates.common.exception.LogServiceException;
import teammates.common.util.Const;
import teammates.common.util.TimeHelper;
import teammates.ui.output.FeedbackSessionLogsData;

/**
 * Action: gets the feedback session logs of feedback sessions of a course.
 */
public class GetFeedbackSessionLogsAction extends Action {
    @Override
    AuthType getMinAuthLevel() {
        return AuthType.LOGGED_IN;
    }

    @Override
    void checkSpecificAccessControl() throws UnauthorizedAccessException {
        if (!userInfo.isInstructor) {
            throw new UnauthorizedAccessException("Instructor privilege is required to access this resource.");
        }

        String courseId = getNonNullRequestParamValue(Const.ParamsNames.COURSE_ID);
        CourseAttributes courseAttributes = logic.getCourse(courseId);

        if (courseAttributes == null) {
            throw new EntityNotFoundException("Course is not found");
        }

        InstructorAttributes instructor = logic.getInstructorForGoogleId(courseId, userInfo.getId());
        gateKeeper.verifyAccessible(instructor, courseAttributes, Const.InstructorPermissions.CAN_MODIFY_STUDENT);
        gateKeeper.verifyAccessible(instructor, courseAttributes, Const.InstructorPermissions.CAN_MODIFY_SESSION);
        gateKeeper.verifyAccessible(instructor, courseAttributes, Const.InstructorPermissions.CAN_MODIFY_INSTRUCTOR);
    }

    @Override
    public JsonResult execute() {
        String courseId = getNonNullRequestParamValue(Const.ParamsNames.COURSE_ID);
        if (logic.getCourse(courseId) == null) {
            throw new EntityNotFoundException("Course not found");
        }
        String email = getRequestParamValue(Const.ParamsNames.STUDENT_EMAIL);
        if (email != null && logic.getStudentForEmail(courseId, email) == null) {
            throw new EntityNotFoundException("Student not found");
        }
        String feedbackSessionName = getRequestParamValue(Const.ParamsNames.FEEDBACK_SESSION_NAME);
        if (feedbackSessionName != null && logic.getFeedbackSession(feedbackSessionName, courseId) == null) {
            throw new EntityNotFoundException("Feedback session not found");
        }
        String startTimeStr = getNonNullRequestParamValue(Const.ParamsNames.FEEDBACK_SESSION_LOG_STARTTIME);
        String endTimeStr = getNonNullRequestParamValue(Const.ParamsNames.FEEDBACK_SESSION_LOG_ENDTIME);
        long startTime;
        long endTime;
        try {
            startTime = Long.parseLong(startTimeStr);
            endTime = Long.parseLong(endTimeStr);
        } catch (NumberFormatException e) {
            throw new InvalidHttpParameterException("Invalid start or end time", e);
        }
        // TODO: we might want to impose limits on the time range from startTime to endTime

        if (endTime < startTime) {
            throw new InvalidHttpParameterException("The end time should be after the start time.");
        }

        long earliestSearchTime = TimeHelper.getInstantDaysOffsetBeforeNow(Const.LOGS_RETENTION_PERIOD.toDays())
                .toEpochMilli();
        if (startTime < earliestSearchTime) {
            throw new InvalidHttpParameterException(
                    "The earliest date you can search for is " + Const.LOGS_RETENTION_PERIOD.toDays() + " days before today."
            );
        }

        try {
            List<FeedbackSessionLogEntry> fsLogEntries =
                    logsProcessor.getFeedbackSessionLogs(courseId, email, startTime, endTime, feedbackSessionName);
            Map<FeedbackSessionAttributes, List<FeedbackSessionLogEntry>> groupedEntries =
                    groupFeedbackSessionLogEntries(courseId, fsLogEntries);
            FeedbackSessionLogsData fslData = new FeedbackSessionLogsData(groupedEntries);
            return new JsonResult(fslData);
        } catch (LogServiceException e) {
            return new JsonResult(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private Map<FeedbackSessionAttributes, List<FeedbackSessionLogEntry>> groupFeedbackSessionLogEntries(
            String courseId, List<FeedbackSessionLogEntry> fsLogEntries) {
        List<FeedbackSessionAttributes> feedbackSessions = logic.getFeedbackSessionsForCourse(courseId);
        Map<FeedbackSessionAttributes, List<FeedbackSessionLogEntry>> groupedEntries = new HashMap<>();
        for (FeedbackSessionAttributes feedbackSession : feedbackSessions) {
            groupedEntries.put(feedbackSession, new ArrayList<>());
        }
        for (FeedbackSessionLogEntry fsLogEntry : fsLogEntries) {
            FeedbackSessionAttributes fs = fsLogEntry.getFeedbackSession();
            if (groupedEntries.get(fs) != null) {
                groupedEntries.get(fs).add(fsLogEntry);
            }
        }
        return groupedEntries;
    }
}
