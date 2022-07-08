#@String(label="Username", value="dornier", persist=false) USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="Image ID", value=119273) id

// create the client and connect to the host
Client user_client = new Client()
host = "omero-server.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected "+ host
	
	try{
		// Create the image Wrapper object
		ImageWrapper img_wpr = user_client.getImage(id)
		
		// Get dataset information from the image ID
		List<DatasetWrapper> dataset_wpr_list = img_wpr.getDatasets(user_client)
		// print dataset ID and dataset name
		dataset_wpr_list.each{println("dataset name : "+it.getName()+", dataset id : "+it.getId())};
		
		// Get dataset information from the image ID
		List<ProjectWrapper> project_wpr_list = img_wpr.getProjects(user_client)
		// print dataset ID and dataset name
		project_wpr_list.each{println("Project name : "+it.getName()+", project id : "+it.getId())};
		
	} finally{
		user_client.disconnect()
		println "Disonnected from "+host
	}
	
}


import fr.igred.omero.*
import fr.igred.omero.repository.*
import omero.gateway.model.DatasetData;
