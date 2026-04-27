#@String(label="Host", value="omero-server.epfl.ch", persist=true) host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset"}) object_type
#@String(label="Object ID or object(s) URL", value=119273) ids
#@Float(label="Pixel size X (um)", value=0.5) pxlSizeX
#@Float(label="Pixel size Y (um)", value=0.5) pxlSizeY


/* Code description
 *  
 * Set new pixel size to the selected image or to all images within the selected dataset
 * 
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2024.02.23
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
			switch (object_type){
				case "image":	
					processImage(user_client, user_client.getImage(id))
					break	
				case "dataset":
					processDataset(user_client, user_client.getDataset(id))
					break
			}		
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
 * Modify the pixel size of an image
 * 
 * */
def processImage(user_client, image_wpr){
	// get the current pixel size
	def currentPixelSizeX = image_wpr.getPixels().getPixelSizeX().getValue()
	def currentPixelSizeY = image_wpr.getPixels().getPixelSizeY().getValue()
	
	// set the new pixel size
	def pixelSizeXLength = new LengthI(pxlSizeX, UnitsLength.MICROMETER)
	def pixelSizeYLength = new LengthI(pxlSizeY, UnitsLength.MICROMETER)
	image_wpr.getPixels().asDataObject().setPixelSizeX(pixelSizeXLength)
	image_wpr.getPixels().asDataObject().setPixelSizeY(pixelSizeYLength)
	
	// save the new pixel size
	user_client.save(image_wpr.asIObject())
	println "Pixel size modification - Image id "+id+" - Replace "+currentPixelSizeX+" x "+currentPixelSizeY+" by "+pxlSizeX+" x "+pxlSizeY+" - DONE !"
}



/**
 * Process the full images with the specified dataset
 * 
 * */
def processDataset(user_client, dataset_wpr){
	dataset_wpr.getImages(user_client).each{ image_wpr ->
		processImage(user_client , image_wpr)
	}
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import omero.model.LengthI
import omero.model.enums.UnitsLength