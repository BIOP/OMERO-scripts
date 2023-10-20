#@Long(label="Median radius", value=3) radius

// clear the roiManager
roiManager("reset");
// get the current image name
imageName = getTitle();
// duplicate the first channel
run("Duplicate...", "  channels=1");
// get the name of the duplicated channel
duplicateImageName = getTitle();
// "cleaning" using filtering
run("Median...", "radius="+radius);
// Threshold
setAutoThreshold("Huang dark");
//Convert to mask
setOption("BlackBackground", true);
run("Convert to Mask");
//morphological operations
run("Open");
run("Fill Holes");
// analyze particules on the first slice only
run("Set Measurements...", "area mean min centroid center perimeter display redirect=None decimal=3");
run("Analyze Particles...", "display overlay add slice");
// close duplicated image
close(duplicateImageName);
// clear resultsTable
run("Clear Results");
// select stack
selectWindow(imageName);
// select the first channel
Stack.setChannel(1);
// measure and add metrics of the first channel to teh ResultsTable
roiManager("Measure");
// select the second channel
Stack.setChannel(2);
// measure and add metrics of the second channel to teh ResultsTable
roiManager("Measure");
// select the third channel
Stack.setChannel(3);
// measure and add metrics of the third channel to teh ResultsTable
roiManager("Measure");