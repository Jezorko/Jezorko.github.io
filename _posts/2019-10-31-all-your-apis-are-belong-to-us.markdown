---
layout: post
title:  "All your API are belong to us"
date:   2019-06-16 09:11:59 +0100
categories: api design
---

When was the last time you have counted the dependencies of your project?

Chances are 
Adding a new dependency to a project these days is a 
Have you ever tried to use a new library and wondered: „is the author trying to”

Just look at Java's Stream API:

{% highlight java %}
Stream.of("A", "B", "A")
      .collect(Collectors.toSet())
      .forEach(System.out::println);
{% endhighlight %}