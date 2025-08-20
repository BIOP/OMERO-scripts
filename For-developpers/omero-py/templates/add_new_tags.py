from omero.gateway import BlitzGateway
import omero
import traceback


def list_tags_from_image(conn, image_id):
    image_wrapper = conn.getObject("Image", image_id)
    annotation_list = image_wrapper.listAnnotations()
    tag_list = []
    for annotation in annotation_list:
        if annotation.OMERO_TYPE == omero.model.TagAnnotationI:
            tag_list.append(annotation.getTextValue())

    return tag_list


def add_tags(conn, image_id, tags):
    image = conn.getObject("Image", image_id)
    group_tags = conn.getObjects("TagAnnotation")
    image_tags = list_tags_from_image(conn, image.getId())
    tags_to_add = []
    for tag in tags:
        if tag.lower() not in [tag_to_add.getTextValue().lower() for tag_to_add in tags_to_add]:
            if tag.lower() not in [tag_to_add.getTextValue().lower() for tag_to_add in group_tags]:
                tag_wrapper = omero.gateway.TagAnnotationWrapper()
                tag_wrapper.setTextValue(tag)
                tag_wrapper.save()
            else:
               tag_wrapper = next(group_tag for group_tag in group_tags if group_tag.getTextValue.lower() == tag.lower())

            if tag_wrapper.getTextValue().lower() not in [tag_to_add.getTextValue().lower() for tag_to_add in image_tags]:
                tags_to_add.append(tag_wrapper)

            image.linkAnnotation(tag_wrapper)



def run_script():
    host = "localhost"
    conn = BlitzGateway("username", "password", host=host, port=4064, secure=True)
    conn.connect()

    image_id = 111
    new_tags = ["a", "b", "c"]

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            add_tags(conn, image_id, new_tags)
        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnected from {host}")


if __name__ == "__main__":
    run_script()

