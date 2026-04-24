#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(choices={"Only my files", "All files within my group"}, style="radioButtonHorizontal", persist=false) choice


/*  Code description
 *   
 *  This script aims at deleting all orphaned attachments from your OMERO account / group.
 *  An orphaned attachment is an attachment that is NOT linked to ANY image/container i.e. not used anymore on OMERO
 *  If your are a group owner, you'll have the choice to delete all orphaned files from your group 
 *  The script prints the oprhaned files in the console
 *  
 *  For more in depth scripting, please look at the user script "Delete Orphaned attachments"
 *  
 *  
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2023.02.27
 * Version: 1.0.0
 * 
 * -----------------------------------------------------------------------------
 * Copyright (c) 2026 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
 * All rights reserved.
 * 
 * Licensed under the BSD-3-Clause License:
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided 
 * that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer 
 *    in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, 
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * -----------------------------------------------------------------------------
 */


// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected to "+host
	
	try{
		// find all files on current omero group
		def fileWrapperList = []
		Map<long, ExperimenterData> experimenters = new HashMap<>()
		def groupData
		def experimenterData
		
		// get user account / group
		groupData = user_client.getGateway().getAdminService(user_client.getCtx()).getGroup(user_client.getCurrentGroupId())
		experimenterData = user_client.getGateway().getAdminService(user_client.getCtx()).getExperimenter(user_client.getUser().getId())
		experimenters.put(user_client.getUser().getId(), experimenterData)
		
		// retrieve group attachments
		if(choice.equals("All files within my group")){
			println "Get all files from your group"
			fileWrapperList = getGroupAttachments(user_client)
		}
		// retrieve user attachments
		else{
			println "Get all files from your account"
			fileWrapperList = getExperimenterAttachments(user_client)	
		}
			
		List<FileAnnotationWrapper> orphanedFiles = []
		List<FileAnnotationWrapper> linkedFiles = []
		Map<long, List<IObject>> linkedParentsMap = new HashMap<>()

		// filter orphaned and linked files
		println "Filter all orphaned files and get links from non-orphaned files..."
		(orphanedFiles, linkedFiles, linkedParentsMap) = filterOrphanedAttachments(user_client, fileWrapperList)
		
		// sort both list by file name
		orphanedFiles.sort(Comparator.comparing(FileAnnotationWrapper::getFileName));
		linkedFiles.sort(Comparator.comparing(FileAnnotationWrapper::getFileName));
		
		println "orphaned files : " + orphanedFiles.stream().map(FileAnnotationWrapper::getFileName).collect(Collectors.toList()).join(" ; ")
		println "linked files : " + linkedFiles.stream().map(FileAnnotationWrapper::getFileName).collect(Collectors.toList()).join(" ; ")
		
	}finally{
		// disconnect
		user_client.disconnect()
		println "Disconnected from "+host
	}
}else{
	println "Not able to connect to "+host
}
return


/**
 * retrieve all attachments within the default group
 */
def getGroupAttachments(user_client){
	return user_client.findByQuery("select an from FileAnnotation as an join fetch an.file")
								.stream()
								.map(FileAnnotationData::new)
								.map(FileAnnotationWrapper::new)
								.collect(Collectors.toList())
}


/**
 * retrieve all attachments owner by the current logged in user
 */
def getExperimenterAttachments(user_client){
	return user_client.findByQuery("select an from FileAnnotation as an left outer join fetch an.file" +
														" left outer join fetch an.details.owner as o " +
												         "where o.id = "+ user_client.getUser().getId())
								.stream()
								.map(FileAnnotationData::new)
								.map(FileAnnotationWrapper::new)
								.collect(Collectors.toList())
}


/**
 * sort orphaned from non orphaned attachments and get all the link to images / container for non orphaned files
 */
def filterOrphanedAttachments(user_client, fileWrapperList){			
	List<FileAnnotationWrapper> orphanedFilesWpr = new ArrayList<>()
	List<FileAnnotationWrapper> linkedFilesWpr = new ArrayList<>()
	Map<long, List<IObject>> linkedParentsMap = new HashMap<>()
	
	fileWrapperList.each{
		def listOfParents = user_client.findByQuery("select link.parent from ome.model.IAnnotationLink link " +
					"where link.child.id=" +it.getId())
		
		// orphaned files
		if(listOfParents.size() == 0)
			orphanedFilesWpr.add(it)
		else{
			// linked files
			linkedFilesWpr.add(it)
			linkedParentsMap.put(it.getId(), listOfParents)
		}
	}
	
	return [orphanedFilesWpr, linkedFilesWpr, linkedParentsMap]
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.FileAnnotationData
import omero.gateway.model.ExperimenterData
import omero.model.IObject
import java.util.stream.Collectors;
import java.io.File;
