package org.sakaiproject.scorm.ui.reporting.util;

import java.util.Iterator;
import java.util.List;

import org.sakaiproject.scorm.model.api.ActivitySummary;
import org.sakaiproject.wicket.markup.html.repeater.util.EnhancedDataProvider;

public class SummaryProvider extends EnhancedDataProvider {

	private static final long serialVersionUID = 1L;

	private final List<ActivitySummary> summaries;
	
	public SummaryProvider(List<ActivitySummary> summaries) {
		this.summaries = summaries;
	}
	
	public Iterator<ActivitySummary> iterator(int first, int count) {
		return summaries.subList(first, first + count).iterator();
	}

	public int size() {
		return summaries.size();
	}

}
