from omero.gateway import BlitzGateway


def run_script():
    object_type = "Image"
    object_id = 303

    conn = BlitzGateway("username", "password", host="localhost", port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        try:
            # search in all the user's group
            conn.SERVICE_OPTS.setOmeroGroup('-1')

            omero_object = conn.getObject(object_type, object_id)
            print_parent(omero_object, object_type)
        finally:
            conn.close()


def print_parent(omero_object, object_type):
    if object_type == 'Image' or \
            object_type == 'Dataset' or \
            object_type == 'Well' or \
            object_type == 'Plate':
        print(f'{object_type} : {omero_object.getName()} | {omero_object.getId()}')
        parent = omero_object.getParent()
        print_parent(parent, parent.OMERO_CLASS)
    if object_type == 'Screen' or object_type == 'Project':
        print(f'{object_type} : {omero_object.getName()} | {omero_object.getId()}')


if __name__ == "__main__":
    run_script()
