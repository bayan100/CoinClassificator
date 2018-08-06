package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import android.support.annotation.NonNull;

import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QueuedList {
    private List<List<Point>> data = new ArrayList<>();
    private List<QueuedListNode> operations = new ArrayList<>();

    // hold the reference to
    private List<List<Point>> toMerge0 = new ArrayList<>();
    private List<List<Point>> toMerge1 = new ArrayList<>();

    void add(int index){
        operations.add(new QueuedListNode(QueuedListNode.Operation.ADD, index));
    }

    void insert(int index, @NonNull List<Point> item){
        operations.add(new QueuedListNode(QueuedListNode.Operation.INSERT, index, item));
    }

    void remove(int index){
        operations.add(new QueuedListNode(QueuedListNode.Operation.REMOVE, index));
    }

    void merge(int upperIndex){
        operations.add(new QueuedListNode(QueuedListNode.Operation.MERGE, upperIndex));
    }

    void split(int lowerIndex){
        operations.add(new QueuedListNode(QueuedListNode.Operation.SPLIT, lowerIndex));
    }

    List<Point> get(int index){
        return data.get(index);
    }

    int size(){
        return data.size();
    }

    void clear(){
        data.clear();
    }

    void commit(List<List<Point>> result){
        // now execute every queued operation
        // keep an eye on removals and additions
        int counter = 0;
        for (int i = 0; i < operations.size(); i++) {
            QueuedListNode node = operations.get(i);
            switch (node.operation){
                case ADD: data.add(node.index, new ArrayList<Point>());
                    counter++;
                    break;
                case INSERT: data.get(node.index).addAll(node.additionalData);
                    break;
                case REMOVE:
                    // check if list is part of a merge
                    int ind0 = toMerge0.indexOf(data.get(node.index + counter));
                    int ind1 = toMerge1.indexOf(data.get(node.index + counter));
                    if(ind0 != -1){
                        Collections.reverse(toMerge0.get(ind0));
                        toMerge1.get(ind0).addAll(0, toMerge0.get(ind0));

                        toMerge0.remove(ind0);
                        toMerge1.remove(ind0);
                    }
                    else if(ind1 != -1){
                        Collections.reverse(toMerge1.get(ind1));
                        toMerge0.get(ind1).addAll(0, toMerge1.get(ind1));

                        toMerge1.remove(ind1);
                        toMerge0.remove(ind1);
                    }
                    else
                        result.add(data.get(node.index + counter));

                    data.remove(node.index + counter);
                    counter--;
                    break;
                case MERGE:
                    // invert the upper contour and append to lower
                    Collections.reverse(data.get(node.index + counter));
                    data.get(node.index + counter - 1).addAll(data.get(node.index + counter));

                    // remove the lower
                    data.remove(node.index + counter);
                    counter--;
                    break;
                case SPLIT:
                    // add new contour
                    data.add(node.index, new ArrayList<Point>());
                    counter++;

                    // save to be merged
                    toMerge0.add(data.get(node.index - 1));
                    toMerge1.add(data.get(node.index));
                    break;
            }
        }
        operations.clear();
    }

    void finalCommit(List<List<Point>> result){
        for (int i = 0; i < toMerge0.size(); i++) {
            // find them in the result list
            int ind = result.indexOf(toMerge0.get(i));
            if(ind != -1)
                result.remove(ind);
            ind = result.indexOf(toMerge1.get(i));
            if(ind != -1)
                result.remove(ind);

            Collections.reverse(toMerge0.get(i));
            toMerge1.get(i).addAll(toMerge0.get(i));
            result.add(toMerge1.get(i));

            // remove from current to later not add again
            ind = data.indexOf(toMerge0.get(i));

            if(ind != -1)
                data.remove(ind);
            ind = data.indexOf(toMerge1.get(i));

            if(ind != -1)
                data.remove(ind);
        }

        // add the remaining open ends (without partner) to result
        result.addAll(data);
    }
}