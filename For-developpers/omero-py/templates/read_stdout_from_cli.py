from io import StringIO
import sys


class Capturing(list):
    def __enter__(self):
        self._stdout = sys.stdout
        sys.stdout = self._stringio = StringIO()
        return self

    def __exit__(self, *args):
        self.extend(self._stringio.getvalue().splitlines())
        del self._stringio    # free up some memory
        sys.stdout = self._stdout


def run_script():
    with Capturing() as output:
        print('hello world')

    print('displays on screen')

    with Capturing(output) as output:  # note the constructor argument
        print('hello world2')

    print('done')
    print('output:', output)

    report_list = []
    report_list.append('omero.cmd.Duplicate Image:42638,42639 ok')
    report_list.append('Steps: 14')
    report_list.append('  ImageAnnotationLink:58214,58215')
    report_list.append('  Image:42646,42647')

    extract_duplicate_image_ids(report_list)


def extract_duplicate_image_ids(report_list):
    to_search = "Image:"
    ids = []
    for line in report_list:
        if line.strip().startswith(to_search):
            ids = line.strip().replace(to_search, "")
            return ids.split(",")

    return ids


if __name__ == "__main__":
    run_script()