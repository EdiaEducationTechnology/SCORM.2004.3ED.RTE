package org.sakaiproject.scorm.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.adl.sequencer.ISeqActivity;
import org.adl.sequencer.ISeqActivityTree;
import org.adl.sequencer.SeqActivity;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.event.api.LearningResourceStoreService;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Actor;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Context;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Object;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Result;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Statement;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Verb;
import org.sakaiproject.event.api.LearningResourceStoreService.LRS_Verb.SAKAI_VERB;
import org.sakaiproject.scorm.model.api.ActivityReport;
import org.sakaiproject.scorm.model.api.ActivitySummary;
import org.sakaiproject.scorm.model.api.ContentPackageManifest;
import org.sakaiproject.scorm.model.api.Progress;
import org.sakaiproject.scorm.model.api.ScoBean;
import org.sakaiproject.scorm.model.api.Score;
import org.sakaiproject.scorm.model.api.SessionBean;
import org.sakaiproject.scorm.service.api.ScormApplicationService;
import org.sakaiproject.scorm.service.api.ScormLearningEventListener;
import org.sakaiproject.scorm.service.api.ScormResultService;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;

public class LrsScormLearningEventListener implements ScormLearningEventListener {

	static final Log LOG = LogFactory.getLog(LrsScormLearningEventListener.class);

	protected ScormResultService scormResultService;

	protected ScormApplicationService scormApplicationService;

	protected LearningResourceStoreService learningResourceStoreService;

	protected ServerConfigurationService serverConfigurationService;

	protected ToolManager toolManager;

	protected UserDirectoryService userDirectoryService;

	public void init() {
		scormApplicationService.addListener(this);
	}

	@Override
	public void onCommit(SessionBean sessionBean, ScoBean scoBean) {
		// DO NOTHING

	}

	@Override
	public void onInitialize(SessionBean sessionBean, ScoBean scoBean) {
		try {
			registerUsageStatement(sessionBean, scoBean, new LRS_Verb(SAKAI_VERB.initialized));
		} catch (Throwable e) {
			LOG.error("onInitialize", e);
		}
	}

	private LRS_Object getLRS_Object(SessionBean sessionBean, ScoBean scoBean) {
		String contentPackageId = sessionBean.getContentPackage().getResourceId();
		String scoId = scoBean.getScoId();
		LRS_Object object = new LRS_Object("scorm://" + contentPackageId + "/" + scoId, "scorm");
		object.setActivityName(getNameMap(sessionBean.getActivityTitle()));
		// TODO: object definition.
		return object;
	}

	private LRS_Object getLRS_ObjectOverview(SessionBean sessionBean, ScoBean scoBean) {
		String contentPackageId = sessionBean.getContentPackage().getResourceId();
		LRS_Object object = new LRS_Object("scorm://" + contentPackageId, "scorm");
		object.setActivityName(getNameMap(sessionBean.getContentPackage().getTitle()));
		return object;
	}

	protected HashMap<String, String> getNameMap(String value) {
		HashMap<String, String> name = new HashMap<String, String>();
		name.put(Locale.getDefault().toString(), value);
		return name;
	}

	private LRS_Context getLRS_Context(SessionBean sessionBean, ScoBean scoBean) {
		String uri = getCurrentPlacementUrl();
		return new LRS_Context("parent", uri);
	}

	protected String getCurrentPlacementUrl() {
		String context = toolManager.getCurrentPlacement().getContext();
		String toolId = toolManager.getCurrentPlacement().getToolId();
		return serverConfigurationService.getPortalUrl() + "/site/" + context + "/tool/" + toolId;
	}

	private LRS_Actor getLRS_Actor(SessionBean sessionBean) {
		try {
			User user = userDirectoryService.getUser(sessionBean.getLearnerId());
			String actorEmail;
			if (StringUtils.isNotEmpty(user.getEmail())) {
				actorEmail = user.getEmail();
			} else {
				// no email set - make up something like one
				String server = serverConfigurationService.getServerName();
				if ("localhost".equals(server)) {
					server = "tincanapi.dev.sakaiproject.org";
				} else {
					server = serverConfigurationService.getServerId() + "." + server;
				}
				actorEmail = user.getId() + "@" + server;
			}
			LRS_Actor actor = new LRS_Actor(actorEmail);
			if (StringUtils.isNotEmpty(user.getDisplayName())) {
				actor.setName(user.getDisplayName());
			}
			return actor;
		} catch (UserNotDefinedException e) {
			LOG.warn("Unable to find user", e);
		}
		return null;
	}

	@Override
	public void onSetValue(String dataModelElement, String value, SessionBean sessionBean, ScoBean scoBean) {
		// 

	}

	@Override
	public void onTerminate(SessionBean sessionBean, ScoBean scoBean) {
		try {
			// SCO LEVEL REPORTING
			if (registerResultStatement(sessionBean, scoBean)) {
				List<ActivitySummary> activitySummaries = scormResultService.getActivitySummaries(sessionBean.getContentPackage().getContentPackageId(),
				        sessionBean.getLearnerId(), sessionBean.getAttemptNumber());
				LRS_Verb verb = getLRS_Verb(activitySummaries, sessionBean);
				LRS_Actor actor = getLRS_Actor(sessionBean);
				LRS_Object object = getLRS_ObjectOverview(sessionBean, scoBean);
				LRS_Statement statement = new LearningResourceStoreService.LRS_Statement(actor, verb, object);
				statement.setContext(getLRS_Context(sessionBean, scoBean));

				statement.setResult(getResult(activitySummaries, sessionBean));
				learningResourceStoreService.registerStatement(statement, "sakai.scorm");

			}
			// SCO TERMINATION
			registerUsageStatement(sessionBean, scoBean, new LRS_Verb(SAKAI_VERB.terminated));
		} catch (Throwable e) {
			LOG.error("onTerminate", e);
		}
	}

	private LRS_Result getResult(List<ActivitySummary> activitySummaries, SessionBean sessionBean) {
		List<ISeqActivity> activities = getActivities(sessionBean);
		int totalCount = activities.size();
		int finishedCount = 0;
		for (ISeqActivity activity : activities) {
			ActivitySummary activitySummary = findFinishedActivitySummary(activitySummaries, activity);
			if (activitySummary != null) {
				finishedCount++;
			}
		}
		float scaled = 0;
		if (totalCount != 0) {
			scaled = ((float) (finishedCount)) / totalCount;
		}
		LRS_Result result = new LRS_Result(finishedCount == totalCount);
		result.setScore(scaled, finishedCount, 0, totalCount);
		return result;
	}

	protected ActivitySummary findFinishedActivitySummary(List<ActivitySummary> activitySummaries, ISeqActivity activity) {
		ActivitySummary rv = null;
		for (ActivitySummary activitySummary : activitySummaries) {
			// (“completed”, “incomplete”, “not attempted”, “unknown”
			if (StringUtils.equals(activitySummary.getScoId(), activity.getID())
			        && StringUtils.equals(activitySummary.getCompletionStatus(), "completed")) {
				rv = activitySummary;
			}
		}
		return rv;
	}

	protected List<ActivitySummary> findActivitySummaries(List<ActivitySummary> activitySummaries, ISeqActivity activity) {
		List<ActivitySummary> rv = new ArrayList<ActivitySummary>();
		for (ActivitySummary activitySummary : activitySummaries) {
			if (StringUtils.equals(activitySummary.getScoId(), activity.getID())) {
				rv.add(activitySummary);
			}
		}
		return rv;
	}

	protected List<ISeqActivity> getActivities(SessionBean sessionBean) {
		ContentPackageManifest manifest = sessionBean.getManifest();
		ISeqActivityTree actTreePrototype = manifest.getActTreePrototype();

		List<String> globalObjectives = actTreePrototype.getGlobalObjectives();
		List<ISeqActivity> activities = new ArrayList<ISeqActivity>();
		if (globalObjectives != null) {
			for (String globalObjective : globalObjectives) {
				List<ISeqActivity> acts = getActitvities(actTreePrototype, globalObjective);
				activities.addAll(acts);
			}
		} else {
			List<ISeqActivity> acts = getActitvities(actTreePrototype, null);
			activities.addAll(acts);
		}

		addRecursive(activities, actTreePrototype.getRoot());

		return activities;
	}

	protected void addRecursive(List<ISeqActivity> activities, SeqActivity root) {
		addRecursive(activities, root, new HashSet<String>());
	}

	protected void addRecursive(List<ISeqActivity> activities, SeqActivity root, Set<String> antiRecursion) {
		if (root == null || antiRecursion.contains(root.getID()))
			return;
		activities.add(root);
		antiRecursion.add(root.getID());
		List<SeqActivity> children = root.getChildren(true);
		if (children != null) {
			for (SeqActivity child : children) {
				addRecursive(activities, child, antiRecursion);
			}
		}
	}

	protected List<ISeqActivity> getActitvities(ISeqActivityTree actTreePrototype, String globalObjective) {
		List<String> activityIds = actTreePrototype.getObjMap(globalObjective);
		List<ISeqActivity> acts = new ArrayList<ISeqActivity>();
		if (activityIds != null) {
			for (String activityId : activityIds) {
				ISeqActivity activity = actTreePrototype.getActivity(activityId);
				if (activity != null) {
					acts.add(activity);
				}
			}
		}
		return acts;
	}

	private LRS_Verb getLRS_Verb(List<ActivitySummary> activitySummaries, SessionBean sessionBean) {
		// TODO Auto-generated method stub
		return new LRS_Verb(SAKAI_VERB.progressed);
	}

	protected boolean registerResultStatement(SessionBean sessionBean, ScoBean scoBean) {
		ActivityReport activityReport = scormResultService.getActivityReport(sessionBean.getContentPackage().getContentPackageId(), sessionBean.getLearnerId(),
		        sessionBean.getAttemptNumber(), scoBean.getScoId());
		LRS_Verb verb = getLRS_Verb(activityReport);
		LRS_Actor actor = getLRS_Actor(sessionBean);
		LRS_Object object = getLRS_Object(sessionBean, scoBean);
		LRS_Statement statement = new LearningResourceStoreService.LRS_Statement(actor, verb, object);
		statement.setContext(getLRS_Context(sessionBean, scoBean));

		statement.setResult(getResult(activityReport));
		learningResourceStoreService.registerStatement(statement, "sakai.scorm");
		return StringUtils.equals(verb.getId(), new LRS_Verb(SAKAI_VERB.completed).getId()) ;
	}

	protected LRS_Verb getLRS_Verb(ActivityReport activityReport) {
		// (“completed”, “incomplete”, “not attempted”, “unknown”
		LRS_Verb verb;
		String completionStatus = activityReport.getProgress().getCompletionStatus();
		if (StringUtils.equals("completed", completionStatus)) {
			verb = new LRS_Verb(SAKAI_VERB.completed);
		} else {
			verb = new LRS_Verb(SAKAI_VERB.progressed);
		}
		return verb;
	}

	protected LearningResourceStoreService.LRS_Result getResult(ActivityReport activityReport) {

		Progress progress = activityReport.getProgress();

		Score score = activityReport.getScore();
		String completionStatus = progress.getCompletionStatus();
		boolean completed = StringUtils.equals("completed", completionStatus);
		LearningResourceStoreService.LRS_Result result = new LearningResourceStoreService.LRS_Result(completed);
		// “passed”, “failed”, “unknown”
		String successStatus = progress.getSuccessStatus();
		result.setSuccess(StringUtils.equals("passed", successStatus));
		if (score.getRaw() != -1) {
			result.setRawScore(score.getRaw());
		}
		if (score.getScaled() != -1) {
			result.setScore((float) score.getScaled());
		}
		if (result.getDuration() != -1) {
			result.setDuration(result.getDuration());
		}
		result.setResponse(result.getResponse());
		return result;
	}

	protected void registerUsageStatement(SessionBean sessionBean, ScoBean scoBean, LRS_Verb verb) {
		LRS_Actor actor = getLRS_Actor(sessionBean);
		LRS_Context context = getLRS_Context(sessionBean, scoBean);
		LRS_Object object = getLRS_Object(sessionBean, scoBean);
		LRS_Statement statement = new LearningResourceStoreService.LRS_Statement(actor, verb, object);
		statement.setContext(context);
		learningResourceStoreService.registerStatement(statement, "sakai.scorm");
	}

	public void setScormResultService(ScormResultService scormResultService) {
		this.scormResultService = scormResultService;
	}

	public void setScormApplicationService(ScormApplicationService scormApplicationService) {
		this.scormApplicationService = scormApplicationService;
	}

	public void setLearningResourceStoreService(LearningResourceStoreService learningResourceStoreService) {
		this.learningResourceStoreService = learningResourceStoreService;
	}

	public void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
		this.serverConfigurationService = serverConfigurationService;
	}

	public void setToolManager(ToolManager toolManager) {
		this.toolManager = toolManager;
	}

	public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
		this.userDirectoryService = userDirectoryService;
	}

}
