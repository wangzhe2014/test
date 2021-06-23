package com.example.nettyclient;

import com.google.protobuf.InvalidProtocolBufferException;

public class TestStudentProto {

    private static byte[] encode(Student.SubscribeReq req) {
        return req.toByteArray();
    }

    private static Student.SubscribeReq decode(byte[] body) throws InvalidProtocolBufferException {
        return Student.SubscribeReq.parseFrom(body);
    }

    private static Student.SubscribeReq createStudent() {
        Student.SubscribeReq.Builder builder = Student.SubscribeReq.newBuilder();
        builder.setSubReqId(10);
        builder.setUserName("张三");
        builder.setUserAge("12");
        return builder.build();
    }

    public static void main(String[] args) throws InvalidProtocolBufferException {
        Student.SubscribeReq student = createStudent();
        System.out.println("编码之前:"+ student.getUserAge() + student.getUserName());
        byte[] encode = encode(student);
        System.out.println("编码:"+ encode);
        Student.SubscribeReq decode = decode(encode);
        System.out.println("解码:"+ decode);
        System.out.println(student.equals(decode));
    }
}
