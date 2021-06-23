package com.example.nettyclient;

import org.springframework.util.StringUtils;

public class Test {
    public static String getFileExtName1(String imageData) {
        if (StringUtils.isEmpty(imageData)) {
            return null;
        }
        String[] data = imageData.split(",");
        if (StringUtils.isEmpty(data[0])) {
            return null;
        }
        String[] data1 = data[0].split(";");
        if (StringUtils.isEmpty(data1[0])) {
            return null;
        }
        String[] ext = data1[0].split("/");
        return ext[1];
    }

    public static void main(String[] args) {
        int j = 0;
        for (int i=0; i<3; i++) {
            try {
                if (i ==0) {
                    continue;
                }
                System.out.println("11111");
            }catch (Exception e) {
                j = 1;
                System.out.println("22222");
                break;
            }finally {
                System.out.println("33333");
            }
        }
    }
}
