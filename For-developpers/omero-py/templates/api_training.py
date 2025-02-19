from io import BytesIO

import omero
from omero.gateway import BlitzGateway
from PIL import Image
import traceback


def run_script():
    # Initial code for the api training
    # connect to OMERO server
    host = "localhost"
    conn = BlitzGateway("username", "password", host=host, port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            # set the fetch in all the available group for the connected user
            conn.SERVICE_OPTS.setOmeroGroup(-1)  # has to be modified to fetch the correct group

            # get one single image
            image_id = 2659
            image_wrapper = conn.getObject("Image", image_id)

            # get a list of images (generator object)
            dataset_id = 806
            list_images = conn.getObjects("image", opts={'dataset': dataset_id})

            for image in list_images:
                print("image ID : " + str(image.getId()))

            # get the parent object assuming that the image has a unique parent
            image_wrapper.getParent()

            # get the parent objects
            list_parents = image_wrapper.listParents()
            for parent in list_parents:
                print("parent dataset : " + parent.getName())

            # get the parent links
            list_links = image_wrapper.getParentLinks()
            # there is no datasetImageLinkWrapper object. .
            # So, the link object is a raw-type object (from database side)
            for link in list_links:
                print("parent link (dataset) : " + link.parent.getName().getValue())
                print("child link (image) : " + link.child.getName().getValue())

            # get the size of the image hyperstack
            width = image_wrapper.getSizeX()
            height = image_wrapper.getSizeY()
            slices = image_wrapper.getSizeZ()
            frames = image_wrapper.getSizeT()
            channels = image_wrapper.getSizeC()

            # get the pixel size
            # to get the units, simply turn the parameter to True
            pixel_size_x = image_wrapper.getPixelSizeX(units=True)
            print(pixel_size_x)
            pixel_size_y = image_wrapper.getPixelSizeY()
            print(pixel_size_y)

            # fetch and save image thumbnail
            size = (width * 0.1, height * 0.1)
            thumbnail = image_wrapper.getThumbnail(size)
            rendered_thumb = Image.open(BytesIO(thumbnail))
            # rendered_thumb.show()           # shows a pop-up
            rendered_thumb.save("thumbnail.jpg")

            # fetch all annotations from the current image
            # and check their type
            annotation_list = image_wrapper.listAnnotations()
            for annotation in annotation_list:
                print(annotation.getId(), annotation.OMERO_TYPE)
                if annotation.OMERO_TYPE == omero.model.TagAnnotationI:
                    print("this a tag :" + annotation.getTextValue())
                if annotation.OMERO_TYPE == omero.model.MapAnnotationI:
                    for kvp in annotation.getValue():
                        print("this a map :" + kvp[0] + " : " + kvp[1])

            # No option is actually available
            # So, it is not possible to use conn.getObjects() for any annotation with opts={}
            # If one want to fetch specifically kvp from on particular image, one need to use the query service
            #
            # list_key_values = conn.getObjects("mapannotation")
            # for keyVal in list_key_values:
            #     print(keyVal.getTextValue())

            # corresponding query to get specific key-value pairs from a certain image
            qs = conn.getQueryService()
            query = "select distinct a from Annotation a join a.mapValue mv where a.id in " \
                    "(select link.child from ImageAnnotationLink link where link.parent=%s)" % image_id
            kvps = qs.findAllByQuery(query, omero.sys.ParametersI(), conn.SERVICE_OPTS)
            for kvp in kvps:
                for nameVal in kvp.mapValue:
                    print(nameVal.name)
                    print(nameVal.value)

            # get the acquisition date object
            date = image_wrapper.getAcquisitionDate()  # datetime.datetime object
            print(f"Acquisition date {date}")

            # create a new key-value pair
            map = omero.gateway.MapAnnotationWrapper()
            namespace = omero.constants.metadata.NSCLIENTMAPANNOTATION  # default namespace
            map.setNs(namespace)
            map.setValue([["my new key", "some value"], ["key2", "val2"]])

            # link the new annotation to the image
            image_wrapper.linkAnnotation(map)

        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnect from {host}")
    else:
        print("Not able to connect to OMERO server. Please check your credentials and hostname")


if __name__ == "__main__":
    run_script()
