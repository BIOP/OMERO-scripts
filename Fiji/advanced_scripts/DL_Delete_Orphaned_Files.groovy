#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Boolean(label="Delete orphaned files", value=false, persist=false) toDelete
#@Boolean(label="Search files for your group", value=false) groupDeletion
#@Boolean(label="Save lists as csv", value=true) toCsv
#@File(label="Path to save csv", style='directory', value="Choose a file") pathToFolder


/* 
 * == INPUTS ==
 *  - credentials 
 *  - Your choice about orphaned files deletion
 *  - Your choice about where to search files (in your group or just for you)
 *    By default, it will only search all files you own
 *  - Your choice about saving file lists as csv
 *  - Path to save csv files
 * 
 * == OUTPUTS ==
 *  - orphaned files deletion from OMERO
 *  - lists of deleted files and remaining files as cs file
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.9.1 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV - PTECH - BIOP 
 * 27.02.2023
 * 
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2022
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
 */


// Connection to server
server = "omero-server"
host = server+".epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "\nConnected to "+host
	
	try{
		// find all files on current omero group
		def fileWrapperList
		String nameId = ""
		Map<long, ExperimenterData> experimenters = new HashMap<>()
		
		if(groupDeletion){
			println "Get all files from your group"
			fileWrapperList = user_client.findByQuery("select an from FileAnnotation as an join fetch an.file")
								.stream()
								.map(FileAnnotationData::new)
								.map(FileAnnotationWrapper::new)
								.collect(Collectors.toList())
			
			group = user_client.getGateway().getAdminService(user_client.getCtx()).getGroup(user_client.getCurrentGroupId())
			nameId = "_group_"+group.getName().getValue()
		}
		else{
			println "Get all files from your account"
			fileWrapperList = user_client.findByQuery("select an from FileAnnotation as an left outer join fetch an.file" +
														" left outer join fetch an.details.owner as o " +
												         "where o.id = "+ user_client.getUser().getId())
								.stream()
								.map(FileAnnotationData::new)
								.map(FileAnnotationWrapper::new)
								.collect(Collectors.toList())
			
			experimenter = user_client.getGateway().getAdminService(user_client.getCtx()).getExperimenter(user_client.getUser().getId())
			experimenters.put(user_client.getUser().getId(), experimenter)
			nameId = "_user_"+experimenter.getOmeName().getValue()
		}
			
		List<FileAnnotationWrapper> orphanedFiles = new ArrayList<>()
		List<FileAnnotationWrapper> linkedFiles = new ArrayList<>()
		Map<long, List<IObject>> linkedParentsMap = new HashMap<>()

		// filter orphaned and linked files
		println "Find all orphaned files"
		fileWrapperList.each{
			def listOfParents = user_client.findByQuery("select link.parent from ome.model.IAnnotationLink link " +
						"where link.child.id=" +it.getId())
			
			// orphaned files
			if(listOfParents.size() == 0)
				orphanedFiles.add(it)
			else{
				// linked files
				linkedFiles.add(it)
				linkedParentsMap.put(it.getId(), listOfParents)
			}
		}
		
		// sort both list by file name
		orphanedFiles.sort(Comparator.comparing(FileAnnotationWrapper::getFileName));
		linkedFiles.sort(Comparator.comparing(FileAnnotationWrapper::getFileName));
				
		if(toCsv){
			// send csv file of all deleted files
			println "Send list of deleted files to .csv"
			String listOfDeletedFiles = "File name,File ID,File size (bytes),File format,Owner\n"
			orphanedFiles.each{file->
				listOfDeletedFiles += file.getFileName() + "," + file.getFileID() + "," + file.getFileSize() + "," + file.getFileFormat()
				
				// check if experimenter is already loaded and load it in case if it is not 				
				def experimenter
				if(!experimenters.containsKey(file.getOwner().getId())){
					experimenter = user_client.getGateway().getAdminService(user_client.getCtx()).getExperimenter(file.getOwner().getId())
					experimenters.put(file.getOwner().getId(), experimenter)
				}
				else
					experimenter = experimenters.get(file.getOwner().getId())
				
				listOfDeletedFiles += "," + experimenter.getOmeName().getValue() + "\n"
			}
			writeCSVFile(pathToFolder, "Deleted_files_from_"+server+nameId, listOfDeletedFiles)
			
			// send csv file of all linked files
			println "Send list of linked files to .csv"
			String listOfLinkedFiles = "File name,File ID,File size (bytes),File format,Owner,Parent_Name_Id\n"
			
			linkedFiles.each{file ->
				listOfLinkedFiles += file.getFileName() + "," + file.getFileID() + "," + file.getFileSize() + "," + file.getFileFormat() 
				
				// check if experimenter is already loaded and load it in case if it is not 
				def experimenter
				if(!experimenters.containsKey(file.getOwner().getId())){
					experimenter = user_client.getGateway().getAdminService(user_client.getCtx()).getExperimenter(file.getOwner().getId())
					experimenters.put(file.getOwner().getId(), experimenter)
				}
				else
					experimenter = experimenters.get(file.getOwner().getId())
				
				listOfLinkedFiles += "," + experimenter.getOmeName().getValue()
				
				// add parents to the csv file
				linkedParents = linkedParentsMap.get(file.getId())
				linkedParents.each{link -> 
					if(link instanceof omero.model.ExperimenterI)
							listOfLinkedFiles += "," + link.class.toString().replace("class omero.model.","") + "_" + link.getOmeName().getValue() + "_" + link.getId().getValue()
					else
						listOfLinkedFiles += "," + link.class.toString().replace("class omero.model.","") + "_" + link.getName().getValue() + "_" + link.getId().getValue()
				}
				
				listOfLinkedFiles += "\n"
			}
			writeCSVFile(pathToFolder, "Links_files_on_"+server+nameId, listOfLinkedFiles)
		}
		
		// delete orphaned files if there are some
		if(toDelete){
			println "Delete all orphaned files"
			if(!orphanedFiles.isEmpty())
				user_client.delete(orphanedFiles.stream().map(FileAnnotationWrapper::asIObject).collect(Collectors.toList()))
			else
				println "No files to delete"
		}
			
	} finally{
		user_client.disconnect()
		println "Disconnected "+host
	}
	
	return
	
}else{
	println "Not able to connect to "+host
}


/**
 * Save a csv file in the given path, with the given name
 */
def writeCSVFile(path, name, fileContent){
	// create the file locally
    File file = new File(path.toString() + File.separator + name + ".csv");

   // write the file
   BufferedWriter buffer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
   buffer.write(fileContent + "\n");

   // close the file
   buffer.close();
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;