"""
Script performing a maximum intensity projection along Z axis and annotate the projection image

It is based on this script https://gist.github.com/will-moore/4eb2fe61cd35cabd4682083b1a45e0e9
written by Will Moore, OME team.

See Also https://forum.image.sc/t/maximum-intensity-projection-feature-in-omero-web/54392/5
"""

from omero.gateway import BlitzGateway
import numpy as np
import omero.scripts as scripts
from omero.rtypes import rstring, rlong, robject
import omero


P_DATA_TYPE = "Data_Type"
P_IDS = "IDs"
P_FULL_STACK = "Full stack projection"
P_START_Z = "Starting Z position"
P_END_Z = "Ending Z position"
P_TRANSFER_ANN = "Transfer annotations to projection image (ROIs excluded)"


def do_max_intensity_projection(conn, script_params):
    """
    Main loop.
    Performs the Z-projection between start and end slice.
    Adds tag and kvps to the projected image
    Transfer annotation from source to projection image

    Parameters
    ----------
    conn: _BlitzGateway Object
        OMERO object handling the connection
    script_params: Dict of String
        user inputs

    Returns
    -------
    new_image_list: List of ImageWrapper
        Projection images
    """
    conn.SERVICE_OPTS.setOmeroGroup(-1)
    object_id_list = script_params[P_IDS]
    is_full_stack = bool(script_params[P_FULL_STACK])
    if not is_full_stack:
        start_z = int(script_params[P_START_Z])
        end_z = int(script_params[P_END_Z])
        if start_z > end_z:
            end_tmp = end_z
            end_z = start_z
            start_z = end_tmp

    new_image_list = []
    
    for image_id in object_id_list:
        
        image = conn.getObject("Image", image_id)
        print("Source image", image_id, image.name)

        group_id = image.getDetails().group.id.val
        print('group_id', group_id)
        conn.SERVICE_OPTS.setOmeroGroup(group_id)

        dataset = None
        parents = list(image.listParents())
        if len(parents) > 0:
            dataset = parents[0]._obj
            print("Dataset", dataset.id.val)

        sizeZ = image.getSizeZ()
        sizeC = image.getSizeC()
        sizeT = image.getSizeT()

        clist = range(sizeC)
        pixels = image.getPrimaryPixels()

        if is_full_stack:
            start_z = 1
            end_z = sizeZ

        tag = "max_projection"
        tag_ann = omero.gateway.TagAnnotationWrapper()
        tag_ann.setValue(tag)

        current_tags = conn.getObjects("TagAnnotation", attributes={"textValue": tag})
        for current_tag in current_tags:
            tag_ann = current_tag
            break

        def plane_gen(start, end):
            for c in clist:
                for t in range(sizeT):
                    # get Z-stack...
                    zct_list = [(z + start - 1, c, t) for z in range(end-start+1)]
                    # planes is a generator - no data loaded yet...
                    planes = pixels.getPlanes(zct_list)
                    data = []
                    for p in planes:
                        # could add a sleep here if you want to reduce load on server?
                        data.append(p)
                    z_stack = np.stack(data, axis=0)
                    # return the Max-Intensity projection for C and T
                    yield np.amax(z_stack, axis=0)

        # Use sourceImageId to copy channels metadata etc.
        extension_name = image.name.split(".")[-1]
        short_image_name = image.name.replace(extension_name, "")
        new_image = conn.createImageFromNumpySeq(
            plane_gen(start_z, end_z), f'{short_image_name}_proj.{extension_name}', sizeZ=1, sizeC=sizeC, sizeT=sizeT,
            sourceImageId=image_id, channelList=clist, dataset=dataset)

        print("Projected Image", new_image.getId(), new_image.getName())

        # adding tags
        print("Adding tag: ", tag)
        new_image.linkAnnotation(tag_ann)

        # adding key-value pairs
        kvps = [["Source image ID", f"{image_id}"],
                  ["Source image", f"{image.name}"],
                  ["Projection type", "Maximum intensity"],
                  ["Z-slices", f"{start_z}-{end_z}"]]
        print("Adding KVPs: ", kvps)
        adding_kvp(kvps, "z_projection", new_image)

        # Transfer annotations from parent to projection image
        if bool(script_params[P_TRANSFER_ANN]):
            print("Transferring annotations from source to projection image")
            for ann in image.listAnnotations():
                # create a new annotation for KVPs
                if ann.OMERO_TYPE == omero.model.MapAnnotationI:
                    kvps = []
                    for kvp in ann.getValue():
                        kvps.append([kvp[0], kvp[1]])

                    adding_kvp(kvps, ann.getNs(), new_image)
                else:
                    new_image.linkAnnotation(ann)

        new_image_list.append(new_image)
        
    return new_image_list


def adding_kvp(kvps, ns, image_wrapper):
    """
    Adding a group of KVPs to an image

    Parameters
    ----------
    kvps : List of list of String
        The kvps to link
    ns: String
        the namespace under which kvps will be linked
    image_wrapper: omero.model.<ObjectType>
        OMERO object to link kvps to

    """
    # create a new key-value pair
    map_ann = omero.gateway.MapAnnotationWrapper()
    map_ann.setNs(ns)
    map_ann.setValue(kvps)
    image_wrapper.linkAnnotation(map_ann)


def run_script():

    data_types = [rstring('Image')]
    
    client = scripts.client(
        'Maximum intensity projection',
        """
    Script performing a maximum intensity projection along Z axis. 
        """,
        
        scripts.String(
            P_DATA_TYPE, optional=False, grouping="1",
            description="Choose source of images",
            values=data_types, default="Image"),
            
        scripts.List(
            P_IDS, optional=False, grouping="1",
            description="Image ID.").ofType(rlong(0)),

        scripts.Bool(
            P_FULL_STACK, grouping="2",
            description="If doing projection on the full stack, the starting "
                        "slice and ending slice are not taken into account",
            default=True),

        scripts.Int(
            P_START_Z, grouping="2.1",
            description="First projected slice", default=1, min=1),

        scripts.Int(
            P_END_Z, grouping="2.2",
            description="Last projected slice", default=1, min=1),

        scripts.Bool(
            P_TRANSFER_ANN, grouping="3",
            description="Copy annotation from source image to projection image. "
                        "\nROIs and image description are not transferred.",
            default=False),

        authors=["William Moore, Rémy Dornier"],
        institutions=["University of Dundee, EPFL - BIOP"],
        contact="omero@groupes.epfl.ch"
    )

    try:
        # process the list of args above.
        script_params = {}
        for key in client.getInputKeys():
            if client.getInput(key):
                script_params[key] = client.getInput(key, unwrap=True)

        # wrap client to use the Blitz Gateway
        conn = BlitzGateway(client_obj=client)
        print("script params")
        for k, v in script_params.items():
            print(k, v)
        
        image_list = do_max_intensity_projection(conn, script_params)
        final_message = "Created Image(s)"
        
        for image in image_list:
            final_message += " : " +image.name 
        
        client.setOutput("Message", rstring(final_message))
        client.setOutput("Image", robject(image_list[0]._obj))

    finally:
        client.closeSession()


if __name__ == "__main__":
    run_script()

