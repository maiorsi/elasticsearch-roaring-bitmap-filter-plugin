from pyroaring import BitMap
import base64

if __name__ == "__main__":
    bm = BitMap([1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144])
    
    print (base64.b64encode(BitMap.serialize(bm)))