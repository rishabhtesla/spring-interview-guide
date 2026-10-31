package com.in28minutes.spring.basics.springin5steps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class SpringIn5StepsApplication {

    // @SpringBootApplication scans this package tree for components and wires their dependencies.

    public static void main(String[] args) {

        // Retrieve the managed bean rather than constructing an unwired instance with new.
        ApplicationContext applicationContext = SpringApplication.run(SpringIn5StepsApplication.class, args);

        BinarySearchImpl binarySearch = applicationContext.getBean(BinarySearchImpl.class);

        int result = binarySearch.binarySearch(new int[]{12, 4, 6}, 3);

        System.out.println(result);
    }
}