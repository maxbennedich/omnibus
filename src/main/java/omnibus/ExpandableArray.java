package omnibus;

/** Simple expandable primitive type array. */
class ExpandableArray {
    long[] array = new long[16];
    int size = 0;

    void add(long v) {
        if (size == array.length) {
            long[] newArray = new long[array.length * 2];
            System.arraycopy(array, 0, newArray, 0, array.length);
            array = newArray;
        }
        array[size++] = v;
    }
}
