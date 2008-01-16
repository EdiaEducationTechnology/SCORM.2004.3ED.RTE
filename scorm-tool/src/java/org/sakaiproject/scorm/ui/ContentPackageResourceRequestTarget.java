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
/*
 * The logic in this class was modeled after the Apache Wicket class
 * 	org.apache.wicket.request.target.resource.SharedResourceRequestTarget
 * authored by Eelco Hillenius and distributed under the following license:
 * 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.scorm.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.wicket.IRequestTarget;
import org.apache.wicket.RequestCycle;
import org.apache.wicket.Resource;
import org.apache.wicket.Response;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.markup.html.DynamicWebResource;
import org.apache.wicket.protocol.http.WebResponse;
import org.apache.wicket.request.RequestParameters;
import org.sakaiproject.scorm.exceptions.ResourceNotFoundException;
import org.sakaiproject.scorm.model.api.ContentPackageResource;
import org.sakaiproject.scorm.ui.player.ScormTool;
import org.sakaiproject.scorm.ui.player.pages.PlayerPage;

public class ContentPackageResourceRequestTarget implements IRequestTarget {

	private static Log log = LogFactory.getLog(ContentPackageResourceRequestTarget.class);
	
	private final RequestParameters requestParameters;
	
	private final String resourceName;
	
	public ContentPackageResourceRequestTarget(RequestParameters requestParameters) {
		this.requestParameters = requestParameters;
		
		Map parameters = requestParameters.getParameters();
		
		this.resourceName = (String)parameters.get("resourceName");
	}
	
	public void detach(RequestCycle arg0) {
		
	}
	
	private Resource getResource(ScormTool application) {
		if (log.isDebugEnabled())
			log.debug("Looking up resource by " + resourceName);

		return application.getSharedResources().get(PlayerPage.class, resourceName, null, null, false);
		
		/*ContentPackageResource resource = application.getResourceService().getResource(resourceId, path);

		try {
			if (resource == null || resource.getInputStream() == null)
				return null;
		
		
			final String mimeType = resource.getMimeType();
			final byte[] data = streamBytes(resource.getInputStream());
			
			
			final DynamicWebResource fileResource = new DynamicWebResource() {
					
				private static final long serialVersionUID = 1L;
	
				protected ResourceState getResourceState() {
					return new ResourceState() {
	
						@Override
						public String getContentType() {
							return mimeType;
						}
	
						@Override
						public byte[] getData() {						
							return data;
						}
							
					};
				}				
			};
		
			return fileResource;
			
		} catch (ResourceNotFoundException rnfe) {
			log.error("Could not find resource ", rnfe);
		}
		
		return null;*/
	}
	
	public int hashCode()
	{
		int result = "ContentPackageResourceRequestTarget".hashCode();
		result += resourceName.hashCode();
		return 17 * result;
	}
	
	
	public void respond(RequestCycle requestCycle) {
		ScormTool application = (ScormTool)requestCycle.getApplication();

		Resource resource = getResource(application);
		
		if (resource == null) {
			Response response = requestCycle.getResponse();
			if (response instanceof WebResponse)
			{
				((WebResponse)response).getHttpServletResponse().setStatus(
						HttpServletResponse.SC_NOT_FOUND);
				log.error("Content Package resource not found with resourceName: " + resourceName);
				return;
			}
			else
			{
				throw new WicketRuntimeException("Content Package resource not found with resourceName: " + resourceName);
			}
		}
		
		// set request parameters if there are any
		if (requestParameters != null)
		{
			resource.setParameters(requestParameters.getParameters());
		}

		// let the resource handle the request
		resource.onResourceRequested();
	}
	
	private byte[] streamBytes(InputStream inputStream) {
		int len = 0;
		byte[] buf = new byte[1024];
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		
		try {
			while ((len = inputStream.read(buf)) > 0) {
				byteOut.write(buf,0,len);
			}
			
			inputStream.close();
		} catch (IOException ioe) {
			log.error("Caught an io exception trying to write file into byte array!", ioe);
		}
		
		return byteOut.toByteArray();
	}
	
	public RequestParameters getRequestParameters() {
		return requestParameters;
	}

	
	public String toString()
	{
		return new StringBuilder("[ContentPackageResourceRequestTarget@").append(hashCode()).append(", resourceName=")
			.append(getResourceName()).append("]").toString();
	}

	public String getResourceName() {
		return resourceName;
	}

}
