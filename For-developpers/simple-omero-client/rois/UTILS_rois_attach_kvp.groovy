#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@Long(label="Image ID", value=119273) id
#@String(label="key") key
#@String(label="Value") value
#@String(label="Namespace", required=false) namespace

#@RoiManager rm


/* Code description
 *  
 * It gets ROIs attached to an image on OMERO and attach the same key value pair to each of them
 * 
 *  
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2026.03.31
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
 */


IJ.run("Close All", "");
rm.reset()

// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

// select right namespace
NAMESPACE = namespace
if(namespace == null || namespace.isEmpty() || namespace.trim().isEmpty()){
	NAMESPACE = "openmicroscopy.org/omero/client/mapAnnotation"
}

if (user_client.isConnected()){
	println "Connected to "+host

	try{
		processImage(user_client, user_client.getImage(id))
		println "Processing of image, id "+id+": DONE !"
	} finally {
		user_client.disconnect()
		println "Disconnected from "+host
	}
} else {
	println "Not able to connect to "+host
}
return


def processImage(user_client, image_wpr){
	// clear Fiji env
	IJ.run("Close All", "");
	rm.reset()
	
	// convert imageWrapper to imagePlus
	println image_wpr.getName()
	ImagePlus imp = image_wpr.toImagePlus(user_client);
	imp.show()
	
	// load OMERO rois
	println "Loading existing OMERO-ROIs"
	def omeroRois = image_wpr.getROIs(user_client)
	
	// convert omero ROIs into ImageJ ROIs
	ROIWrapper.toImageJ(omeroRois).each{rm.addRoi(it)}
	
	omeroRois.eachWithIndex{roiWrapper, idx ->
		saveKvpsOnOmero(user_client, roiWrapper, key, value+"_"+idx, namespace)
	}
}

/**
 * Add a list of tags to the specified image
 * 
 */
def saveKvpsOnOmero(user_client, roiWrapper, key, value, namespace){
	// create a map of kvp
	def kvpMap = new HashMap<>()
	kvpMap.put(key, value)
	
	// generate a MapAnnotationWrapper
	def kvpList = []
	def listofEntry = new ArrayList<>(kvpMap.entrySet())
	MapAnnotationWrapper mapAnnWrapper = new MapAnnotationWrapper((Collection<? extends Entry<String, String>>)listofEntry)
	mapAnnWrapper.setNameSpace(NAMESPACE)
	kvpList.add(mapAnnWrapper)
	
	// link kvp to the object
	roiWrapper.link(user_client, (MapAnnotationWrapper[])kvpList.toArray())
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import ij.*
import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.TagAnnotationData;
import java.util.stream.Collectors
import java.util.Collection
import java.util.Map.Entry