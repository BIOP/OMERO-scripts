#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@File(label="Folder",style="directory") rawFolder
#@String(choices={"New dataset", "Existing dataset"}, style="radioButtonHorizontal") choice
#@String(label="FOR AN EXISTING DATASET : ", visibility=MESSAGE, required=false) msg1
#@Long(label="Dataset ID", value=119273, required=false) datasetId
#@String(label="FOR A NEW DATASET : ", visibility=MESSAGE, required=false) msg2
#@Boolean(label="Use folder name",value=true,required=false) useFolderName
#@String(label="Dataset name if new dataset", value="name", required=false) newDatasetName
#@Long(label="Project ID if new dataset", value=119273, required=false) projectId
#@String(label="TAG CONFIGURATION : ", visibility=MESSAGE, required=false) msg3
#@Boolean(label="Image tags",value=true) imageTags
#@Boolean(label="Serie tags",value=false) serieTags
#@Boolean(label="Folder tags",value=false) folderTags

/* == CODE DESCRIPTION ==
 * This script batch imports images on OMERO and automatically add tags to the images.
 * All images, included those in child folders, will be uploaded IN THE SAME DATASET ON OMERO
 * The name of the image, as well as the name of the serie for fileset images, must be separated with underscores "_" to be parsable.
 * All commom images are supported EXCEPT : 
 * 		- .xml
 * 		- .xcde
 * 		- .ome
 * SCREENS ARE NOT SUPPORTED by this script
 * 
 * For ome.tiff images ONLY :
 * 		- group them by fileset and copy each filset in a separate folder
 * 		- the script will upload only the first image of the fileset ; all images within the fileset are automatically uploaded
 * 
 * The name of the image, as well as the name of the serie, is parsed with underscores and tokens are used as tags on OMERO.
 * If the name of the image is : tk1_tk2-tk3_tk4.tif [tk5_tk6] then tags are : tk1 / tk2-tk3 / tk4 / tk5 / tk6
 * The folder hierarchy (with subfolders) is also added as tags on OMERO but THERE IS NO PARSING OF THE NAMES (no convention for folder name)
 * 		
 * 		
 * == INPUTS ==
 *  - credentials 
 *  - folder to upload. 
 *  - You have the choice to create a new dataset on OMERO or to re-use an existing one
 * 		- If you choose to create a new dataset 
 * 			- enter the name of the new dataset OR use the name of the folder
 * 			- enter the ID of the project where to put the new dataset
 * 		- If you choose to upload projections in an existing
 * 			- enter the ID of the dataset
 * 	- You can configure the tags you want to add to your images on OMERO
 * 		- Image tags : onyl image name (without serie name) is parsed (with underscore parsing)
 * 		- Folder hierarchy : The folder and sub-folders name are used as tags (WITHOUT underscore parsing)
 * 		- Serie name : the name of the serie is parsed (with underscore parsing)
 * 	
 * 
 * == OUTPUTS ==	
 *  - image on OMERO
 *  - Tags on OMERO
 * 
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.14.2 https://github.com/GReD-Clermont/simple-omero-client
 *  
 *  
 *  = AUTHOR INFORMATION =
 * Code written by Rémy Dornier - EPFL - SV - PTECH - BIOP
 * date : 2023-08-10
 * version : v1.1
 * 
 * = HISTORY =
 * - 2023.08.10 : First release --v1.0
 * - 2023.10.04 : Fix bug on GUI + remove numeric tags (i.e. only numbers) --v1.1
 * 
 */

// check the validity of user parameters
if(choice == "Existing dataset" && (datasetId == null || datasetId < 0)){
	println "The dataset ID is not correct"
	return
}

if(choice == "New dataset" && (projectId == null || projectId < 0)){
	println "The project ID is not correct"
	return
}

if(choice == "New dataset" && !useFolderName && (newDatasetName == null || newDatasetName.isEmpty())){
	println "The dataset name is not correct"
	return
}


// global constants
SCREEN = "screen"
STANDARD = "standard"
OME_TIFF = "ome_tiff"
def compatibleFormatList =  [".tif", ".tiff", ".ome.tif", ".ome.tiff", ".lif", ".czi", ".nd", ".nd2", ".vsi", ".lsm", ".dv", ".zvi",
	".png", ".jpeg", ".stk", ".gif", ".jp2", ".xml", ".jpg", ".pgm", ".qptiff", ".h5", ".avi", ".ics", ".ids", ".lof", ".oif", ".tf2",
	".tf8", ".btf", ".pic", ".ims", ".bmp", ".xcde", ".ome", ".svs"]
	
	
// Connection to server
host = "omero-server.epfl.ch"
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())


if (user_client.isConnected()){
	println "\nConnected to "+host
		
	try{
		// get images to process
		println("List all images from : " + rawFolder);
		def uList;
		def cMap;
		def imgMap
		(cMap, imgMap, uList) = listImages(rawFolder, compatibleFormatList)
		def option = JOptionPane.YES_OPTION

		// check if there are uncompatible images. In case, inform the user
		if(!uList.isEmpty()){
			def title = "Uncompatible Files"
			def content = "Some files are not considered as images ; they will not be processed. \nDo you want to continue ?"
			option = JOptionPane.showConfirmDialog(new JFrame(), content, title, JOptionPane.YES_NO_OPTION);		
		}

		// check if there are ome-tiff images. In case, inform the user
		if(option == JOptionPane.YES_OPTION){
			def values = cMap.values()
			if(values.contains(OME_TIFF)){
				def title = "OME_TIFF Files"
				def content = "There are some .ome.tiff files. Please, check that all and only files from the fileset are in the same folder. "+
							"If it is not the case, CAMCEL the script, separate them in different folders and restart the script"
				option = JOptionPane.showConfirmDialog(new JFrame(), content, title, JOptionPane.OK_CANCEL_OPTION);		
			}
			
			// upload images
			if(option == JOptionPane.OK_OPTION || option == JOptionPane.YES_OPTION){
				def countRaw = 0
				def datasetWrapper = getOmeroDataset(user_client, choice, newDatasetName, projectId, datasetId, useFolderName, rawFolder)
				cMap.each{parentFolder,type->
					if(type == STANDARD){
						// upload all images
						imgMap.get(parentFolder).each{
							countRaw += uploadImage(user_client, it, rawFolder, datasetWrapper)
						}
					}else if(type == OME_TIFF){
						// upload only the first image as it is part of a fileset and the entire fileset will be uploaded
						def child = imgMap.get(parentFolder)
						countRaw += child.length
						uploadImage(user_client, child.get(0), rawFolder, datasetWrapper)
					}else{
						println "Screens are not supported : "+parentFolder.getAbsolutePath()
					}
				}
				
				println(countRaw+" files uploaded on OMERO");
			}
		}
		
	} finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}
	return
	
}else{
	println "Not able to connect to "+host
}





/**
 * List compatible images to upload within parentFolder
 * 
 */
def listImages(parentFolder, compatibleFormatList){
	def parentFolderMap = new HashMap<>()
	def fileImgMap = new HashMap<>()
	def uncompatibleList = []
	def isScreeningImage = false

	parentFolder.listFiles().each{imgFile->
		// check for file
		if(imgFile.isFile()){
			// remove all hidden files
			if(!imgFile.isHidden()){
				def imgName = imgFile.getName()
				// remove all images belonging to HCS
				if(!isScreeningImage){
					// check file compatibility
					if(isCompatibleImage(imgName, compatibleFormatList)){
						def parent = imgFile.getParentFile()
						// filter HCS file format
						if(imgName.endsWith(".xml") || imgName.endsWith(".xdce") || imgName.endsWith(".ome")){
							println "Screening images not supported"
							isScreeningImage = true
							parentFolderMap.put(parent, SCREEN)
						}else{
							// special treatement for ome-tiff files
							if(imgName.endsWith(".ome.tif") || imgName.endsWith(".ome.tiff")){
								parentFolderMap.put(parent, OME_TIFF)
							}else{
								parentFolderMap.put(parent, STANDARD)
							}
							// add the file to the list
							def files
							if(fileImgMap.containsKey(parent)){
								files = fileImgMap.get(parent)
							}else{
								files = new ArrayList<>()
							}
							files.add(imgFile)
							fileImgMap.put(parent, files)
						}
					}else{
						uncompatibleList.add(imgFile)
						println "Uncompatible file "+imgFile.getAbsolutePath()
					}
				}
			}
		
		// if the file if a directory, recursie call to the method to list child files
		}else if(imgFile.isDirectory() && !imgFile.getName().startsWith("_")){
			def imgMap;
			def cMap;
			def uList
			(cMap, imgMap, uList) = listImages(imgFile, compatibleFormatList)
			parentFolderMap.putAll(cMap)
			fileImgMap.putAll(imgMap)
			uncompatibleList.add(uList)
		}
	}
	
	return [parentFolderMap, fileImgMap, uncompatibleList]
}



/**
 * Check the file compatibility i.e. should only be bio-formats compatible images
 */
def isCompatibleImage(name, compatibleFormatList){
	def lowerCaseName = name.toLowerCase()
	for(String format : compatibleFormatList){
		if(lowerCaseName.endsWith(format))
			return true
	}
	return false
}


/**
 * return the dataset wrapper corresonding a new dataset or to an existing dataset, depending on the user choice
 */
def getOmeroDataset(user_client, choice, newDatasetName, projectId, datasetId, useFolderName, rawFolder){
	// if specified, create a new dataset on OMERO
	def datasetWrapper
	if(choice == "New dataset"){
		def projectWrapper = user_client.getProject(projectId)
        def dataset = new DatasetData();
        
        // name of the dataset
        if(useFolderName)
        	dataset.setName(rawFolder.getName());
        else
        	dataset.setName(newDatasetName);
        
        dataset = user_client.getDm().createDataset(user_client.getCtx(), dataset, projectWrapper.asProjectData())
        def newId = dataset.getId()
        
        datasetWrapper = user_client.getDataset(newId)
        println("Create a new dataset " + datasetWrapper.getName() + ", ID " + newId);
	} else {
		datasetWrapper = user_client.getDataset(datasetId) 
	}
	return datasetWrapper
}


/**
 * Upload the image on OMERO, in the specified dataset
 * 
 */
def saveImageOnOmero(user_client, imgFile, datasetWrapper){
	print("Upload image "+imgFile.getName() + ".......");
    def imageIds = datasetWrapper.importImage(user_client, imgFile.getAbsolutePath())
    println(" Done ! ID "+imageIds);	
    
    return imageIds
}


/**
 * Add a list of tags to the specified image
 * 
 */
def saveTagsOnOmero(tags, imgWpr, user_client){
	def tagsToAdd = []
	
	// get existing tags
	def groupTags = user_client.getTags()
	def imageTags = imgWpr.getTags(user_client)
	
	// find if the tag to add already exists on OMERO. If yes, they are not added twice
	tags.each{tag->
		if(tagsToAdd.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } == null){
			// find if the requested tag already exists
			new_tag = groupTags.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } ?: new TagAnnotationWrapper(new TagAnnotationData(tag))
			
			// add the tag if it is not already the case
			imageTags.find{ it.getName().toLowerCase().equals(new_tag.getName().toLowerCase()) } ?: tagsToAdd.add(new_tag)
		}
		
	}
	
	// add all tags to the image
	tagsToAdd.each{print it.getName()+" ; "}
	imgWpr.addTags(user_client, (TagAnnotationWrapper[])tagsToAdd.toArray())
}


/**
 * upload images, parse the image name and add tags on OMERO
 */
def uploadImage(user_client, imgFile, rawFolder, datasetWrapper){
	def ids = saveImageOnOmero(user_client, imgFile, datasetWrapper)
	def nameToParse = imgFile.getName().split("\\.")[0]

	if(serieTags || folderTags || imageTags){
		ids.each{id->
			print "Create tags from : "
			def imgWrapper = user_client.getImage(id)
			def omeImgName = imgWrapper.getName()
			def tags = []
			
			if(imageTags){
				print "image name, "
				tags = nameToParse.split("_") as List
			}
			
			if(serieTags){
				print "serie name, "
				if(!omeImgName.equals(imgFile.getName())){
					// define the regex
					def pattern = /.*\[(?<serie>.*)\]/
					def regex = Pattern.compile(pattern)
					def matcher = regex.matcher(omeImgName)
					
					if(matcher.find()){
						def serieName = matcher.group("serie")
						tags.addAll(serieName.split("_") as List)
					}
				}
			}
			
			if(folderTags){
				print "folder name, "
				def parentName = rawFolder.getName()
				tags.add(parentName)
				def childHierarchyPath = imgFile.getAbsolutePath().replace(rawFolder.getAbsolutePath(),"").replace(imgFile.getName(),"")
				def folders = childHierarchyPath.split("\\"+File.separator)
				folders.each{folder ->
					if(!folder.isEmpty()){
						tags.add(folder)
					}
				}
			}
			
			// remove tags that are only numbers
			def filteredTags = []
			tags.each{tag->
				try{
					Integer.parseInt(tag)
				}catch(Exception e){
					filteredTags.add(tag)
				}
			
			}
			
			print " :  "
			saveTagsOnOmero(filteredTags, imgWrapper, user_client)
			println "...... Done !"
		}
	}
	
	return ids.size()
}

/*
 * imports
 */
import javax.swing.JOptionPane;
import javax.swing.JFrame;
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.DatasetData
import omero.gateway.model.TagAnnotationData;
import java.util.regex.Matcher;
import java.util.regex.Pattern;