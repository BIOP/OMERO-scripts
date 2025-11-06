#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id


/* 
 * == INPUTS ==
 *  - credentials 
 *  - id
 *  - object type
 * 
 * == OUTPUTS ==
 *  - Replace OMERO.tables by new OMERO.tables using the new version of the omero-ij plugin (v5.8.2) to fix a bug on table size 
 *  and reduce the final size on the server. 
 * 
 * = DEPENDENCIES =
 *  - omero-ij.5.8.2-all.jar
 *  - simple-omero-client-5.14.2 or later : https://github.com/GReD-Clermont/simple-omero-client
 *  - DO NOT ACTIVATE (OR DE-ACTIVATE) THE UPDATE SITE OMERO 5.5-5.6 beacuse the new dependency is not yet installed on it
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 2023.09.06
 * 
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2023
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
 * 
 * == HISTORY ==
 * - 2023.09.06 : First version 
 */

/**
 * Main. Connect to OMERO, get a table and disconnect from OMERO
 * 
 */
 
// Connection to server
host = "omero-server.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "\nConnected to "+host
	
	try{
		switch (object_type){
			case "image":	
				n = processImage(user_client, user_client.getImage(id))
				break	
			case "dataset":
				n = processDataset(user_client, user_client.getDataset(id))
				break
			case "project":
				n = processProject(user_client, user_client.getProject(id))
				break
			case "well":
				n = processWell(user_client, user_client.getWell(id))
				break
			case "plate":
				n = processPlate(user_client, user_client.getPlate(id))
				break
			case "screen":
				n = processScreen(user_client, user_client.getScreen(id))
				break
		}
		println n + " tables have been replaced for "+object_type+ " "+id+", its child and linked containers !"
		
	} finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}

}else{
	println "Not able to connect to "+host
}




/**
 *Get an OMERO.table to display it on ResultsTable on imageJ
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def processTable(user_client, repository_wpr){

	// get the OMERO.table
	def listTableFileWrappers = user_client.getTablesFacility().getAvailableTables(user_client.getCtx(), repository_wpr.asDataObject())
										.stream()
										.map(FileAnnotationWrapper::new)
										.collect(Collectors.toList())		
										
	if(listTableFileWrappers.isEmpty()){
		println repository_wpr.getClass().getSimpleName()+": "+repository_wpr.getName()+" has no tables attached"
		return 0
	}else{
		println repository_wpr.getClass().getSimpleName()+": "+repository_wpr.getName()+" is processed..."
	}
	
	nTableProcessed = 0
	
	listTableFileWrappers.each{oldFileWpr ->
		println("****** Working on table "+oldFileWpr.getFileName()+" *******")
		println("Get the old table :"+oldFileWpr.getFileID())
		TableWrapper oldTable = repository_wpr.getTable(user_client, oldFileWpr.getFileID());
		oldTable.setId(oldFileWpr.getId());
		
		println("Create the new table")
		def newTableWrapper = new TableWrapper(oldTable.createTable())
		newTableWrapper.setId(oldTable.getId())
		newTableWrapper.setName(oldTable.getName())
		
		print("Add the new table to "+repository_wpr.getClass().getSimpleName()+": "+repository_wpr.getName()+"....")
		repository_wpr.addTable(user_client, newTableWrapper)
		long fileId = newTableWrapper.getFileId()
		println fileId
		
		if(fileId > 0){
			// get the OMERO.table
			def newTableFileWpr = user_client.getTablesFacility().getAvailableTables(user_client.getCtx(), repository_wpr.asDataObject())
											.stream()
											.filter(e-> e.getFileID() ==  fileId)
											.map(FileAnnotationWrapper::new)
											.collect(Collectors.toList()).get(0)
			
			
			println("Unlink the old table from "+repository_wpr.getClass().getSimpleName()+": "+repository_wpr.getName())
			repository_wpr.unlink(user_client, oldFileWpr)
			
			nTableProcessed += 1
			nTableProcessed += browseLinkedRepo(user_client, oldFileWpr, newTableFileWpr)
			
			if(oldFileWpr.countAnnotationLinks(user_client) == 0){
				println("Delete the old table from the database")
				user_client.deleteTable(oldTable)
			}
		}else{
			println "An error occured when adding the new table. The old table is not deleted"
		}
	}
	
	return nTableProcessed
}


def browseLinkedRepo(user_client, oldFileWpr, newFileWpr){
	// get all the images / containers / folders linked to the tag to delete
	def imgList = oldFileWpr.getImages(user_client)
	def datasetList = oldFileWpr.getDatasets(user_client)
	def projectList = oldFileWpr.getProjects(user_client)
	def wellList = oldFileWpr.getWells(user_client)
	def plateList = oldFileWpr.getPlates(user_client)
	def screenList = oldFileWpr.getScreens(user_client)
	def plateAcquisitionList = oldFileWpr.getPlateAcquisitions(user_client)
	def folderList = oldFileWpr.getFolders(user_client)
	
	def oldTableName = oldFileWpr.getFileName()
	def oldTableFileId = oldFileWpr.getFileID()
	
	nTotalLinkTable = 0
	
	// replace the old tag by the new one on all images / containers / folders
	nLinkTables = loopOverRepo(user_client, imgList, oldFileWpr, newFileWpr)
	nTotalLinkTable += nLinkTables
	if(nLinkTables == 0)
		println("No more images linked to table "+oldTableFileId)	
	
	nLinkTables = loopOverRepo(user_client, datasetList, oldFileWpr, newFileWpr)
	nTotalLinkTable += nLinkTables
	if(nLinkTables == 0)
		println("No more datasets linked to table "+oldTableFileId)	
	
	nLinkTables = loopOverRepo(user_client, projectList, oldFileWpr, newFileWpr)
	nTotalLinkTable += nLinkTables
	if(nLinkTables == 0)
		println("No more projects linked to table "+oldTableFileId)	

	nLinkTables = loopOverRepo(user_client, wellList, oldFileWpr, newFileWpr)
	nTotalLinkTable += nLinkTables
	if(nLinkTables == 0)
		println("No more wells linked to table  "+oldTableFileId)	

	nLinkTables = loopOverRepo(user_client, plateList, oldFileWpr, newFileWpr)
	nTotalLinkTable += nLinkTables
	if(nLinkTables == 0)
		println("No more plates linked to table "+oldTableFileId)	

	nLinkTables = loopOverRepo(user_client, screenList, oldFileWpr, newFileWpr)
	nTotalLinkTable += nLinkTables
	if(nLinkTables == 0)
		println("No more screens linked to table "+oldTableFileId)	

	nLinkTables = loopOverRepo(user_client, plateAcquisitionList, oldFileWpr, newFileWpr)
	nTotalLinkTable += nLinkTables
	if(nLinkTables == 0)
		println("No more plate aquisitions linked to table "+oldTableFileId)	

	nLinkTables = loopOverRepo(user_client, folderList, oldFileWpr, newFileWpr)
	nTotalLinkTable += nLinkTables
	if(nLinkTables == 0)
		println("No more folders linked to table "+oldTableFileId)	
		
	return nTotalLinkTable
}


/**
 * Parse the repoList to replace the old tag by the new one
 */
def loopOverRepo(user_client, repoWprList, oldFileWpr, newFileWpr){
	// check if the tag has some links
	if(!repoWprList.isEmpty()){
		def className = repoWprList.get(0).getClass().getSimpleName()
		println("The table is linked to "+repoWprList.size()+" "+className)
		
		repoWprList.each{wpr->
			println "Link new table '"+newFileWpr.getFileName()+"':"+newFileWpr.getFileID()+" to "+className+" "+wpr.getName()
			// only link the tag if it not already linked
			wpr.linkIfNotLinked(user_client, newFileWpr)

			println "Unlink old table '"+oldFileWpr.getFileName()+"':"+oldFileWpr.getFileID()+" from "+className+" "+wpr.getName()
			// unlink the tag from the repo
			wpr.unlink(user_client, oldFileWpr)
		}
	} 
	
	return repoWprList.size()
}




/**
 * Delete all tables attached to an image
 */
def processImage(user_client, image_wpr) {
	return processTable(user_client , image_wpr)
}


/**
 * get all images within a dataset
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processDataset( user_client, dataset_wpr ){
	def dataset_table = null;
	def sizeDelAtt = processTable(user_client , dataset_wpr)
	dataset_wpr.getImages(user_client).each{ image_wpr ->
		sizeDelAtt += processImage(user_client , image_wpr)
	}
	
	return sizeDelAtt
}


/**
 * get all datasets within a project
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		project_wpr : OMERO project
 * 
 * */
def processProject(user_client, project_wpr){
	def sizeDelAtt = processTable(user_client , project_wpr)
	project_wpr.getDatasets().each{ dataset_wpr ->
		sizeDelAtt += processDataset(user_client , dataset_wpr)
	}
	
	return sizeDelAtt
}


/**
 * get all images within a well
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		well_wpr_listet_wpr : OMERO list of wells
 * 
 * */
def processWell(user_client, well_wpr_list){	
	def sizeDelAtt = 0	
	well_wpr_list.each{ well_wpr ->		
		sizeDelAtt += processTable(user_client , well_wpr)
		well_wpr.getWellSamples().each{			
			sizeDelAtt += processImage(user_client, it.getImage())		
		}
	}	
	return sizeDelAtt
}


/**
 * get all wells within a plate
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		plate_wpr_list : OMERO list of plates
 * 
 * */
def processPlate(user_client, plate_wpr_list){
	def sizeDelAtt = 0
	plate_wpr_list.each{ plate_wpr ->	
		sizeDelAtt += processTable(user_client , plate_wpr)
		sizeDelAtt += processWell(user_client, plate_wpr.getWells(user_client))
	} 
	return sizeDelAtt
}


/**
 * get all plates within a screen
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		screen_wpr_list : OMERO list of screens
 * 
 * */
def processScreen(user_client, screen_wpr_list){
	def sizeDelAtt = 0
	screen_wpr_list.each{ screen_wpr ->	
		sizeDelAtt += processTable(user_client , screen_wpr)
		sizeDelAtt += processPlate(user_client, screen_wpr.getPlates())
	} 
	return sizeDelAtt
}




/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.annotations.*
import fr.igred.omero.repository.*
import java.util.stream.Collectors
