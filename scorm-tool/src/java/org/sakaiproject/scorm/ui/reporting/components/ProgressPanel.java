/**********************************************************************************
 * $URL:  $
 * $Id:  $
 ***********************************************************************************
 *
 * Copyright (c) 2007 The Sakai Foundation.
 * 
 * Licensed under the Educational Community License, Version 1.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 *      http://www.opensource.org/licenses/ecl1.php
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 **********************************************************************************/
package org.sakaiproject.scorm.ui.reporting.components;

import org.apache.commons.lang.StringUtils;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.StringResourceModel;
import org.sakaiproject.scorm.model.api.Progress;

public class ProgressPanel extends Panel {

	private static final long serialVersionUID = 1L;

	public ProgressPanel(String id, Progress progress) {
		super(id, new CompoundPropertyModel(progress));

		double mark = progress.getProgressMeasure();
		double scale = progress.getCompletionThreshold();

		double percentage = 100.0 * mark / scale;
		
		Label percentCompleteLabel = new Label("percentComplete", new Model("" + percentage));		
		percentCompleteLabel.setVisible(mark != -1 && scale != -1);
		
		add(percentCompleteLabel);
		
		Label successLabel;
		Label completionLabel;
		
		if(StringUtils.isNotEmpty(progress.getSuccessStatus())) {
			successLabel = new Label("successStatus", new StringResourceModel("success.status.label." + progress.getSuccessStatus(), new Model<Progress>(progress)));
		} else {
			successLabel = new Label("successStatus");
		}
		
		if(StringUtils.isNotEmpty(progress.getCompletionStatus())) {
			completionLabel = new Label("completionStatus", new StringResourceModel("success.status.label." + progress.getCompletionStatus(), new Model<Progress>(progress)));
		} else {
			completionLabel = new Label("completionStatus");
		}
		
		successLabel.setVisible(progress.getSuccessStatus() != null && progress.getSuccessStatus().trim().length() != 0);
		completionLabel.setVisible(progress.getCompletionStatus() != null && progress.getCompletionStatus().trim().length() != 0);
		
		add(successLabel);
		add(completionLabel);
	}

}
