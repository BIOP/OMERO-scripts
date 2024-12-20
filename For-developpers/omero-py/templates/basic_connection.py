from omero.gateway import BlitzGateway


def run_script():
    conn = BlitzGateway("username", "password", host="localhost", port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        try:
            print("I'm connected to OMERO !")
        finally:
            conn.close()


if __name__ == "__main__":
    run_script()
