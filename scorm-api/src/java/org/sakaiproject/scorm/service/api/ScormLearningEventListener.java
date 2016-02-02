package org.sakaiproject.scorm.service.api;

import org.sakaiproject.scorm.model.api.ScoBean;
import org.sakaiproject.scorm.model.api.SessionBean;

public interface ScormLearningEventListener {
	public void onCommit(SessionBean sessionBean, ScoBean scoBean);
	public void onInitialize(SessionBean sessionBean, ScoBean scoBean);
	public void onSetValue(String dataModelElement, String value, SessionBean sessionBean, ScoBean scoBean);
	public void onTerminate(SessionBean sessionBean, ScoBean scoBean);
}
