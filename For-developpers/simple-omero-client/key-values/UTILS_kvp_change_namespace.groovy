#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@String(label="Namespace to replace", value = "openmicroscopy.org/omero/client/mapAnnotation") oldNS
#@String(label="New namespace", value = "my new namespace") newNS

/* 
 * == INPUTS ==
 *  - credentials 
 *  - id
 *  - object type
 *  - old namespace to replace
 *  - new namespace
 * 
 * == OUTPUTS ==
 *  - namespace update
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.18.0 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV - PTECH - BIOP 
 * 2024.04.23
 * 
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2024
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
 * - 2024.04.23 : First release
 */

/**
 * Main. Connect to OMERO, add a key-value and disconnect from OMERO
 * 
 */
 
// Connection to server
host = "omero-server-poc.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "\nConnected to "+host
	
	try{
		switch (object_type){
			case "image":	
				processKVP( user_client, user_client.getImage(id) )
				break	
			case "dataset":
				processKVP( user_client, user_client.getDataset(id) )
				break
			case "project":
				processKVP( user_client, user_client.getProject(id) )
				break
			case "well":
				processKVP( user_client, user_client.getWells(id) )
				break
			case "plate":
				processKVP( user_client, user_client.getPlates(id))
				break
			case "screen":
				processKVP( user_client, user_client.getScreens(id))
				break
		}
		println "Updating Key-value pairs namespace for "+object_type+ " "+id+" : DONE !"
		
	} finally{
		user_client.disconnect()
		println "Disonnected from "+host
	}
	
}else{
	println "Not able to connect to "+host
}


/**
 *Add a new one to the last key-values
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def processKVP(user_client, repository_wpr){
	
	// get the current key-value pairs
	List<MapAnnotationWrapper> keyValues = repository_wpr.getMapAnnotations(user_client).stream().filter(e->e.getNameSpace().equals(oldNS)).toList()
	
	if(!keyValues.isEmpty()){
		println "Updating namespace from '"+oldNS+"' to '"+newNS+"'"
		keyValues.each{it.setNameSpace(newNS)}
		
		// update objects
	    List<IObject> objects = keyValues.stream().map(MapAnnotationWrapper::asDataObject).map(MapAnnotationData::asIObject).collect(Collectors.toList())
	    
	    user_client.getDm().updateObjects(user_client.getCtx(), objects, null);
	}else{
		println "No kvps in this namespace : "+oldNS
	}
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.annotations.*
import omero.model.NamedValue
import java.io.*;
import omero.gateway.model.MapAnnotationData;
import omero.model.IObject;
import java.util.stream.Collectors