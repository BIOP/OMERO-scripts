#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id

/* 
 * == INPUTS ==
 *  - credentials 
 *  - id
 *  - object type
 *  - key and value to add to OMERO object
 * 
 * == OUTPUTS ==
 *  - key-value on OMERO
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.12.3 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 05.10.2022
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
 * 
 * == HISTORY ==
 * - 2023-06-16 : Limits the number of call to the OMERO server + update the version of simple-omero-client to 5.12.3 + remove unnecessary imports.
 */

/**
 * Main. Connect to OMERO, delete key-values for the current object and disconnect from OMERO
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
		def n
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
		println n + " key-value pairs deleted for "+object_type+ " "+id + " and its childs"
		
	} finally {
		user_client.disconnect()
		println "Disonnected from "+host
	}

} else {
	println "Not able to connect to "+host
}


/**
 * Delete key-values
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def processImage(user_client, img_wpr) {
	println "Deleting key-values on image " + img_wpr.getId() + " : " + img_wpr.getName()
	List<MapAnnotationWrapper> keyValues = img_wpr.getMapAnnotations(user_client)										   
	user_client.delete((Collection<GenericObjectWrapper<?>>)keyValues)
	
	return keyValues.size()
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
	def sizeKVP = 0
	dataset_wpr.getImages(user_client).each{ img_wpr ->
		sizeKVP += processImage(user_client , img_wpr)
	}
	
	return sizeKVP
}


/**
 * get all datasets within a project
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		project_wpr : OMERO project
 * 
 * */
def processProject( user_client, project_wpr ){
	def sizeKVP = 0
	project_wpr.getDatasets().each{ dataset_wpr ->
		sizeKVP += processDataset(user_client , dataset_wpr)
	}
	
	return sizeKVP
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
	def sizeKVP = 0	
	well_wpr_list.each{ well_wpr ->				
		well_wpr.getWellSamples().each{			
			sizeKVP += processImage(user_client, it.getImage())		
		}
	}	
	return sizeKVP
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
	def sizeKVP = 0
	plate_wpr_list.each{ plate_wpr ->	
		sizeKVP += processWell(user_client, plate_wpr.getWells(user_client))
	} 
	return sizeKVP
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
	def sizeKVP = 0
	screen_wpr_list.each{ screen_wpr ->	
		sizeKVP += processPlate(user_client, screen_wpr.getPlates())
	} 
	return sizeKVP
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
