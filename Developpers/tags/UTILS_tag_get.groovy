#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id

/* 
 * == INPUTS ==
 *  - credentials 
 *  - id
 *  - object type
 * 
 * == OUTPUTS ==
 *  - print tags
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.9.1 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 01.09.2022
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
 * - 2023.06.19 : Remove unnecessary imports
 * - 2023-10-17 : Add popup message at the end of the script and if an error occurs while running
 */


/**
 * Main. Connect to OMERO, process tags and disconnect from OMERO
 * 
 */
 
// Connection to server
host = "omero-server.epfl.ch"
port = 4064

Client user_client = new Client()

try{
	user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
}catch(Exception e){
	JOptionPane.showMessageDialog(null, "Cannot connect to "+host+". Please check your credentials", "ERROR", JOptionPane.ERROR_MESSAGE);
	return
}

hasFailed = false

if (user_client.isConnected()){
	println "\nConnected to "+host
	
	try{
		def tags
		switch (object_type){
			case "image":	
				tags = processTag(user_client, user_client.getImage(id))
				break	
			case "dataset":
				tags = processTag(user_client, user_client.getDataset(id))
				break
			case "project":
				tags = processTag(user_client, user_client.getProject(id))
				break
			case "well":
				tags = processTag(user_client, user_client.getWells(id))
				break
			case "plate":
				tags = processTag(user_client, user_client.getPlates(id))
				break
			case "screen":
				tags = processTag(user_client, user_client.getScreens(id))
				break
		}
		
		println "Tags '"+tags+"' have been successfully retrieved from "+object_type+ " "+id
		
	}catch(Exception e){
		println e
		println getErrorStackTraceAsString(e)
		hasFailed = true
		JOptionPane.showMessageDialog(null, "An error has occurred when getting tags from OMERO. Please look at the logs.", "ERROR", JOptionPane.ERROR_MESSAGE);
	}finally{
		user_client.disconnect()
		println "Disconnected from "+host
		if(!hasFailed) {
			def message = "The tags '"+ tags +"' have been successfully retrieved from OMERO"
			JOptionPane.showMessageDialog(null, message, "The end", JOptionPane.INFORMATION_MESSAGE);
		}
	}
	
}else{
	println "Not able to connect to "+host
	JOptionPane.showMessageDialog(null, "You are not connected to OMERO", "ERROR", JOptionPane.ERROR_MESSAGE);
}

def processTag(user_client, repository_wpr){
	
	// get image tags
	List<TagAnnotationWrapper> image_tags = repository_wpr.getTags(user_client)
	// sort tags
	image_tags.sort{it.getName()}
	// print tags
	println object_type+" tags"
	image_tags.each{println "Name : " +it.getName()+" (id : "+it.getId()+")"}
	
	
	// get all tags within the group
	List<TagAnnotationWrapper> group_tags = user_client.getTags()
	// sort tags
	group_tags.sort{it.getName()}
	// print tags
	println "\ngroup tags"
	group_tags.each{println "Name : " +it.getName()+" (id : "+it.getId()+")"}
	
	return image_tags.collect{e->e.getName()}
}


/**
 * Return a formatted string of the exception
 */
def getErrorStackTraceAsString(Exception e){
    return Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).reduce("",(a, b)->a + "     at "+b+"\n");
}


/*
 * imports
 */
import fr.igred.omero.*
import fr.igred.omero.annotations.*
import javax.swing.JOptionPane; 
