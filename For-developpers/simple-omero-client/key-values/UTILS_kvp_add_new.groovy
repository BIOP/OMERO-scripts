#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@String(label="Key", value = "Key") key
#@String(label="Value", value = "Value") value
#@String(label="Namespace", required=false) namespace


/* Code description
 *  
 * Adds new KVPs to the select object, in a given namespace
 *  
 *
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2022.09.01
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
 * 
 * History
 * - 2023.06.19 : Remove unnecessary imports
 */

/**
 * Main. Connect to OMERO, add a key-value and disconnect from OMERO
 * 
 */
 
// select right namespace
NAMESPACE = namespace
if(namespace == null || namespace.isEmpty() || namespace.trim().isEmpty()){
	NAMESPACE = "openmicroscopy.org/omero/client/mapAnnotation"
}
 
// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())


if (user_client.isConnected()){
	println "\nConnected to "+host
	
	try{
		switch (object_type){
			case "image":	
				saveKvpsOnOmero( user_client, user_client.getImage(id), key, value, namespace )
				break	
			case "dataset":
				saveKvpsOnOmero( user_client, user_client.getDataset(id), key, value, namespace )
				break
			case "project":
				saveKvpsOnOmero( user_client, user_client.getProject(id), key, value, namespace )
				break
			case "well":
				saveKvpsOnOmero( user_client, user_client.getWells(id), key, value, namespace )
				break
			case "plate":
				saveKvpsOnOmero( user_client, user_client.getPlates(id), key, value, namespace )
				break
			case "screen":
				saveKvpsOnOmero( user_client, user_client.getScreens(id), key, value, namespace )
				break
		}
		println "Adding a Key-value pairs for "+object_type+ " "+id+" : DONE !"
		
	} finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}
}else{
	println "Not able to connect to "+host
}
return


def saveKvpsOnOmero(user_client, annotatableWrapper, key, value, namespace){
	// create a map of kvp
	def kvpMap = new HashMap<>()
	kvpMap.put(key, value)
	
	// generate a MapAnnotationWrapper
	def kvpList = []
	def listOfEntry = new ArrayList<>(kvpMap.entrySet())
	MapAnnotationWrapper mapAnnWrapper = new MapAnnotationWrapper((Collection<? extends Entry<String, String>>)listOfEntry)
	mapAnnWrapper.setNameSpace(NAMESPACE)
	kvpList.add(mapAnnWrapper)
	
	// link kvp to the object
	annotatableWrapper.link(user_client, (MapAnnotationWrapper[])kvpList.toArray())
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.annotations.*
import omero.model.NamedValue
import java.io.*;
import java.util.Collection
import java.util.Map.Entry