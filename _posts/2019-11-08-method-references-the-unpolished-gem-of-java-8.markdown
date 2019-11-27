---
layout: post
title:  "Method References - the unpolished gem of Java 8"
date:   2019-11-08 17:16:00 +0100
categories: Java, Language
---

If you were to pick your favorite year from the entire history of mankind, which year would it be?
Most people would probably say 1932, when [the Great Emu War](https://www.nationalgeographic.com.au/history/the-great-australian-emu-war.aspx) was fought in Australia.
This one is definitely in my top 10; however it falls behind 2014 - the year when Java 8 was released.

Among other things, this glorious update added some functional programming capabilities to the language.
Whether or not you consider this a good idea, [Lambda expressions](https://www.jcp.org/en/jsr/detail?id=335) are now an unavoidable reality of (almost) any Java project.

Therefore, we should look for ways to make the code that uses them as clean and concise as possible.

## Lambda expressions and Method References

[Method References](https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.13)—also introduced in Java 8—provide a different syntax for Lambda expressions.
They may be used with any type of method: instance, static or a constructor.
Although often overlooked, I believe they have the power to greatly improve our code.

I will not talk about how to use Method References here.
This has been explained in great detail by others already, for example in [this tutorial from Oracle](https://docs.oracle.com/javase/tutorial/java/javaOO/methodreferences.html).
My goal is to show the advantages of using them and outline ideas on how they could be improved in the future Java releases.

## Why Method References are great

For many, method references are just a bit of syntactic sugar sprinkled about in the language.
Sometimes they will accept their IDE's suggestion and transform a Lambda into one.
A nice-to-have addition, at most.

But when you look at them closely, you might see just how beneficial they are.

### Naming

As Robert C. Martin wrote in his famous book, [Clean Code](https://www.investigatii.md/uploads/resurse/Clean_Code.pdf):

> Names are everywhere in software. We name our variables, our functions, our arguments, classes, and packages.
  We name our source files and the directories that contain them.
  We name our jar files and war files and ear files.
  We name and name and name.
  Because we do so much of it, we’d better do it well.

Simply stated, naming is what programmers do.
It's not an easy task and the code quality greatly depends on it.
Therefore, anything that can reduce the amount of names can make our job easier.

And this is exactly what Method References can help us achieve.
Consider the following code sample:

{% highlight java %}
dogs.stream()
    .filter(dog -> dogValidator.isAGoodBoy(dog))
    .map(dog -> DogHouseFinder.findHouseOf(dog))
    .filter(optionalDogHouse -> optionalDogHouse.isPresent())
    .map(optionalDogHouse -> optionalDogHouse.get())
    .map(dogHouse -> dogHouse.getTreats())
    .forEach(treats -> new Treat().addTo(treats));
{% endhighlight %}

How many new names had to be invented to write only these few lines?
I counted four: `dog`, `optionalDogHouse`, `dogHouse` and `treats`.

One of these names bothers me in particular.
Is the `dogHouse` still `optional` after we already checked its presence?
The type is still `Optional<DogHouse>`, but we already know it cannot be empty!

We can get rid of all these names and confusion by refactoring with Method References:

{% highlight java %}
dogs.stream()
    .filter(dogValidator::isAGoodBoy)
    .map(DogHouseFinder::findHouseOf)
    .filter(Optional::isPresent)
    .map(Optional::get)
    .map(DogHouse::getTreats)
    .forEach(new Treat()::addTo);
{% endhighlight %}

### Refactoring

Modern IDEs are usually shipped with refactoring tools.
The most basic of these is "rename a method".

{% highlight java %}
dogs.stream()
    .map(dog -> dog.getToy())
    .forEach(toy -> toyCleaner.wash(toy));
{% endhighlight %}

Let's say we are adding a new feature that will allow dogs to have multiple toys.
What would happen to the above code if this tool was applied to the `getToy` method and renamed it to `getFavoriteToy`?
This depends on how smart the tool is, of course:
 * if it's smart, it will prompt you with a question "should I rename variable `toy` to `favoriteToy`?"
 * if it's not, the variable name will become outdated and might cause confusion in the future

Replacing this Lambda with a Method Reference removes the need to change the variable's name - because now there is none!
As a result, the tool does not need to bother us about it and the code is just as clear as it previously was.

## How Method References could be even better

As useful of a feature as Method References are, it surprises me that they are so limited.
It might be because they were not a priority for this release.

Below, I present my two ideas on how they could be improved.
Of course, as for any language feature, there are many corner cases that might make these challenging to implement.

### Calling functional interface's methods from the method reference

Sometimes the object's method alone are not enough to implement the logic.
In these cases, we can take one of the few approaches:

 1. create a decorator around the object and add missing methods
 2. write a new method that takes the object as an argument
 3. put the code in-line
 4. extend the offending class

I listed them in order of my own preference.
Each of these has their own advantages and disadvantages.

In many cases, the Functional Interface of our Lambda already provides default methods with necessary logic.
For example, consider a rather common operation: negating a `Predicate`.
When I tried it for the first time, my thoughts went something like this:

{% highlight java %}
dogs.stream()
    .filter(dog -> !veterinarian.isHealthy(dog))
    .forEach(sickDog -> veterinarian.treat(sickDog));
{% endhighlight %}

Alright, I can definitely use a Method Reference in the `forEach`:

{% highlight java %}
dogs.stream()
    .filter(dog -> !veterinarian.isHealthy(dog))
    .forEach(veterinarian::treat);
{% endhighlight %}

Looks good, now the filter...
Oh, it doesn't recognize the type?
Let's try anyway.

{% highlight java %}
dogs.stream()
    .filter(((Predicate<Dog>) veterinarian::isHealthy).negate())
    .forEach(veterinarian::treat);
{% endhighlight %}

Yup, it looks even worse than before.

To get any further, depending on the version of Java you are using, you can either:

 * use the static `not` method of the `Predicate` class (Java 11), or
 * write one yourself

If you choose the former, you'll end up with something similar to:

{% highlight java %}
public static <T> Predicate<T> not(final Predicate<? super T> predicateToNegate) {
    Objects.requireNonNull(predicateToNegate);
    return predicateToNegate.negate()::test;
}
{% endhighlight %}

This will sit in some `PredicateUtils` class that nobody will remember next time the same problem occurs.
Anyway, our code is now a bit shorter, but also slightly more difficult to read:

{% highlight java %}
dogs.stream()
    .filter(not(veterinarian::isHealthy))
    .forEach(veterinarian::treat);
{% endhighlight %}

Except…

> Take all dogs for which not veterinarian says they are healthy

That does not read well at all.

The `java.util.function.Predicate` interface already has a `negate` method.
It seems logical that negating a Method Reference of a `Predicate` should be possible.
Then why can we not write something like this instead?

{% highlight java %}
dogs.stream()
    .filter(veterinarian::isHealthy.negate())
    .forEach(veterinarian::treat);
{% endhighlight %}

Or perhaps by applying static methods like:

{% highlight java %}
dogs.stream()
    .filter(veterinarian:not:isHealthy)
    .forEach(veterinarian::treat);
{% endhighlight %}

The two examples above are obviously not flawless.

The first one requires changes in the type safety mechanisms.
 * How can we be sure what is the type of the Method Reference before calling its method?
 * If it is always the type of method's target Functional Interface, then how would it work in assignment statements?
 * If the type is not arbitrary, how can we know it has the negation method?

The second one forces us to ask: where do we stop?
 * Should we allow multiple static methods to be chained, or just one?
 * If they can be chained, then the feature could be potentially misused to produce unreadable code.
 * If only a one can be used, then we are ridding ourselves of one limitation only to add another.

### Assigning Method References to methods

This one is not as invasive and could nicely simplify the implementation of decorators.
It can be best explained by an example:

{% highlight java %}
final class DogValidator {

    final boolean isAGoodBoy(final Dog dog) {
        return true;
    }

}
{% endhighlight %}

Since this class is `final`, we should use a decorator pattern to extend its' capabilities.
Let us implement one and add a new `isABadBoy` method:

{% highlight java %}
final class EvenBetterDogValidator {

    private final DogValidator dogValidator;

    EvenBetterDogValidator(final DogValidator dogValidator) {
        this.dogValidator = Objects.requireNonNull(dogValidator);
    }

    final boolean isAGoodBoy(final Dog dog) {
        return dogValidator.isAGoodBoy(dog);
    }

    final boolean isABadBoy(final Dog dog) {
        return !isAGoodBoy(dog);
    }

}
{% endhighlight %}

This is a very simple example, but in the real world the decorated classes usually have a multitude of methods.
Would it not be better if we could just assign a Method Reference to `isAGoodBoy`?

{% highlight java %}
final class EvenBetterDogValidator {

    private final DogValidator dogValidator;

    EvenBetterDogValidator(final DogValidator dogValidator) {
        this.dogValidator = Objects.requireNonNull(dogValidator);
    }

    final boolean isAGoodBoy(final Dog dog) = dogValidator::isAGoodBoy;

    final boolean isABadBoy(final Dog dog) {
        return !isAGoodBoy(dog);
    }

}
{% endhighlight %}

Combining it with the ability to modify Method References with static methods, we can further simplify our code:

{% highlight java %}
final class EvenBetterDogValidator {

    private final DogValidator dogValidator;

    EvenBetterDogValidator(final DogValidator dogValidator) {
        this.dogValidator = Objects.requireNonNull(dogValidator);
    }

    final boolean isAGoodBoy(final Dog dog) = dogValidator::isAGoodBoy;

    final boolean isABadBoy(final Dog dog) = this:not:isAGoodBoy;

}
{% endhighlight %}

Even the method parameter names could be made optional for the same benefits I described before: fewer things to name.

A similar feature is already available in Kotlin, called [single-expression functions](https://kotlinlang.org/docs/reference/functions.html#single-expression-functions).
Although they cannot accept method references, these functions can significantly reduce the amount of boilerplate.
This is how our decorator class could look like if it were written in Kotlin: 

{% highlight kotlin %}
class EvenBetterDogValidator(private val dogValidator: DogValidator) {

    fun isAGoodBoy(dog: Dog) = dogValidator.isAGoodBoy(dog);

    fun isABadBoy(dog: Dog) = !isAGoodBoy(dog);

}
{% endhighlight %}

## Summary

I recommend using Method References whenever possible.
Your code will become cleaner and easier to refactor, at no extra cost.
Keep in mind though that they have some limitations and unless those get waived in the next Java releases, some workarounds need to be implemented.

Method references are—in my humble opinion—not a complete feature.
There are some improvements that could be added to allow wider use of them.
