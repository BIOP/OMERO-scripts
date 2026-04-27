#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object ID or object(s) URL", value=119273) ids
#@File(label="Choose the destination folder", style='directory') dir


/* Code description
 *  
 * This script downloads an image from OMERO. If the selected image is a serie (i.e. part of the fileset), 
 * then the entire fileset is downloaded.
 *  
 *
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2023.07.07
 * Version: 1.1.0
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
 * - 2026.04.27 : Support parsing of URL instead of just an ID -v1.1.0
*/

/**
 * Main. Connect to OMERO, process images and disconnect from OMERO
 * 
 */

// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected to "+host
	
	try{
		def idList = []
		try{
			Long.parseLong(ids)
			idList.add(id)
		}catch (Exception e){
			idList = parseURL(ids)
		}
		
		idList.each{id ->
			processImage(user_client, user_client.getImage(id), dir)
		}
	} finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}
}else{
	println "Not able to connect to "+host
}
return


/**
 * Parse OMERO URL to get the list of ids
 */
def parseURL(url){
	def idList = []
	
	// Check that URL is correct
	if (url.contains("?show=")) {
	    def showPart = url.split("\\?show=")[1]
	    
	    // get everything after the |
	    def items = showPart.split("\\|")
	    
	    def results = []
	    
	    // Parse each element
	    items.each { item ->
	        def matcher = (item =~ /^([a-zA-Z]+)-(\d+)$/)
	        if (matcher.matches()) {
	            results << [
	                type: matcher.group(1),
	                id: matcher.group(2).toInteger()
	            ]
	        }
	    }

		// get ids
	    def type = results.collect { it.type }.unique()
	    if(type.size() == 1 && type.get(0).equalsIgnoreCase(object_type)){
	    	idList = results.collect { it.id }
	    } else {
	    	 println "The type of objects in the URL "+type+" does not match with the selected object type "+object_type
	    }
	
	} else {
	    println "The URL doesn't contain '?show='; it's not coming from OMERO."
	}
	return idList
}


/**
 * Download an image from OMERO
 * 
 * inputs
 * 		user_client : OMERO client
 * 		image_wpr : OMERO image
 * 
 * */
def processImage(user_client, image_wpr, dir){
	try{
		if(dir.exists()){
			print "Download image, id " + id + "..."
	        user_client.getGateway().getFacility(TransferFacility.class).downloadImage(user_client.getCtx(), dir.getAbsolutePath(), image_wpr.getId());
	        println " DONE !"
		}
	    else {
	        println ("\nThe following path does not exists : "+path+ ". Cannot download the image")
	    } 
	}catch(Exception e){
    	println ("\nERROR during download from OMERO")
    }
}


/*
 * imports  
 */
import fr.igred.omero.*
import omero.gateway.facility.TransferFacility
