package org.gradle.sample;

import java.util.Iterator;
import com.google.common.collect.Lists;

public class People implements Iterable<Person> {
    public Iterator<Person> iterator() {
        return Lists.newArrayList(new Person("fred")).iterator();
    }
}
