#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="Plate ID", value=119273) id

/* 
 * == INPUTS ==
 *  - credentials 	
 *  - Plate ID
 * 
 * == OUTPUTS ==
 *  - key-value deletion on the plate and all its childs (wells and images)
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.12.3 : https://github.com/GReD-Clermont/simple-omero-client
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
 * - 2023-06-16 : Refactoring to reduce the number of server calls + remove unnecessary imports + update documentation
 */

/**
 * Main. Connect to OMERO, delete all key-values attached to a plate and its respective children and disconnect from OMERO
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
		List<MapAnnotationWrapper> keyValuesToDelete = processPlate( user_client, user_client.getPlate(id))
		deleteKVP(user_client, keyValuesToDelete)
		println keyValuesToDelete.size() + " key-value pairs deleted from plate "+id+" and its childs"
	} finally{
		user_client.disconnect()
		println "Disonnected from "+host
	}	
}else{
	println "Not able to connect to "+host
}


/**
 * get all the key-values attached to the image/container
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def getKVP(user_client, repository_wpr){
	return repository_wpr.getMapAnnotations(user_client)
}


/**
 * delete the specified key-values
 * 
 * inputs
 * 		user_client : OMERO client
 * 		keyValues : list of MapAnnotationWrapper objects corresponding to OMERO key-values
 * 
 * */
def deleteKVP(user_client, keyValues){										   
	user_client.delete((Collection<GenericObjectWrapper<?>>)keyValues)
}


/**
 * process all images within a well
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		well_wpr_list : List of OMERO wells
 * 
 * */
def processWell(user_client, well_wpr_list){
	def listOfKVPToDelete = []	

	well_wpr_list.each{ well_wpr ->	
		println("Process "+well_wpr.getName())	
		// get the key-values attached to the well
		listOfKVPToDelete.addAll(getKVP(user_client, well_wpr))	
		
		well_wpr.getWellSamples().each{	
			// get the key-values attached to all images within the well		
			listOfKVPToDelete.addAll(getKVP(user_client, it.getImage()))	
		}
	}	
	
	return listOfKVPToDelete
}


/**
 * process all wells within a plate
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		plate_wpr : OMERO plate
 * 
 * */
def processPlate(user_client, plate_wpr){
	def listOfKVPToDelete = []
		
	// get the key-values attached to the plate
	listOfKVPToDelete.addAll(getKVP(user_client, plate_wpr))
	
	// get the key-values attached to all wells within the plate
	listOfKVPToDelete.addAll(processWell(user_client, plate_wpr.getWells(user_client)))
	
	return listOfKVPToDelete
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.annotations.*
