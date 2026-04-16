#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id

/* 
 * Gets all KVPs attached to the select object.
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.19.0 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = AUTHOR INFORMATION =
 * Rémy Dornier, EPFL - PTBIOP 
 * 01.09.2022
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
 * 
 * == HISTORY ==
 * 	- 2023.06.19 : Remove unnecessary imports 
 * 	- 2026.04.01 : Update licence and fix typos
 */

/**
 * Main. Connect to OMERO, process tags and disconnect from OMERO
 * 
 */
 
// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "\nConnected to "+host
	
	try{
		switch (object_type){
			case "image":	
				getKVPs(user_client, user_client.getImage(id))
				break	
			case "dataset":
				getKVPs(user_client, user_client.getDataset(id))
				break
			case "project":
				getKVPs(user_client, user_client.getProject(id))
				break
			case "well":
				getKVPs(user_client, user_client.getWell(id))
				break
			case "plate":
				getKVPs(user_client, user_client.getPlate(id))
				break
			case "screen":
				getKVPs(user_client, user_client.getScreen(id))
				break
		}
		println "Processing of key-values for "+object_type+ " "+id+" : DONE !"
		
	} finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}
}else{
	println "Not able to connect to "+host
}
return


/**
 * Print all kvp belogning to the current OMERO object 
 * 
 * */
def getKVPs(user_client, annotatable_wpr){
	// get the current key-value pairs
	List<Map<String, List<String>>> keyValues = annotatable_wpr.getMapAnnotations(user_client).stream()
																	   .map(MapAnnotationWrapper::getContentAsMap)
																	   .toList()
	for(int i = 0; i< keyValues.size();i++){
		println "KeyValue group n° "+(i+1)
		keyValues.get(i).each{key, value ->
			println "Key : "+key+" ; Value : "+value
		}
		println ""
	}
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.annotations.*