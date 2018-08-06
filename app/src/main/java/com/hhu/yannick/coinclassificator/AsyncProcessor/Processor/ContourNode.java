package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.support.annotation.NonNull;

import org.opencv.core.Point;

class ContourNode implements Comparable
{
    int x, y;
    ContourNode[] nodes; // 0 = top left, 1 = top middle, 2 = top right, 3 = middle left, 5 = middle right, 6 = bottom left, 7 = bottom middle, 8 = bottom right
    int size = 0;
    boolean splitNode, lesserSplitNode;

    ContourNode(int x, int y){
        this.x = x;
        this.y = y;

        nodes = new ContourNode[9];
    }

    boolean connectedIn(int xi, int yi){
        int index = (yi + 1) * 3 + xi + 1;
        return nodes[index] != null;
    }

    ContourNode connectedNode(int xi, int yi){
        int index = (yi + 1) * 3 + xi + 1;
        return nodes[index];
    }

    ContourNode connectedRightToLeft(int yi){
        for (int i = -1; i < 2; i++) {
            ContourNode item = connectedNode(i, yi);
            if(item != null)
                return item;
        }
        return null;
    }

    void addNeighbor(ContourNode item){
        int index = (item.y - y + 1) * 3 + item.x - x + 1;
        nodes[index] = item;
        size++;
    }

    @Override
    public int compareTo(@NonNull Object o) {
        if(o.getClass() == ContourNode.class && ((ContourNode)o).y == y && ((ContourNode)o).x == x)
            return 0;
        else if(o.getClass() == Point.class && ((Point)o).y == y && ((Point)o).x == x)
            return 0;
        else
            return -1;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && compareTo(obj) == 0;
    }
}
