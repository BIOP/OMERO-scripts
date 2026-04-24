import skimage as ski
from skimage.measure import find_contours
from skimage.filters import threshold_otsu
from skimage.io import imshow

def main():
    coins = ski.data.coins()
    threshold_value = threshold_otsu(coins)
    contours = find_contours(coins)
    print(contours[100][0][1])

    points = []
    for point in contours[100]:
        points.append(str(point[1]) + "," + str(point[0]))

    print(", ".join(points))


if __name__ == "__main__":
    main()
