package com.hhu.yannick.coinclassificator.AsyncProcessor.Processor;

import org.opencv.core.Point;

import java.util.List;

class QueuedListNode {
    enum Operation{
        ADD,
        INSERT,
        REMOVE,
        MERGE,
        SPLIT
    }

    Operation operation;
    int index;
    List<Point> additionalData;

    QueuedListNode(Operation operation){
        this.operation = operation;
    }

    QueuedListNode(Operation operation, int index){
        this.operation = operation;
        this.index = index;
    }

    QueuedListNode(Operation operation, int index, List<Point> additionalData){
        this.operation = operation;
        this.index = index;
        this.additionalData = additionalData;
    }
}