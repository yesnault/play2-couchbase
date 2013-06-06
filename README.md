Couchbase Plugin for Play framework 2.1
---------------------------------------

in your `project/Build.scala` file add dependencies and resolvers like :

```scala

import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "shorturls"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    jdbc, anorm,
    "org.ancelin.play2.couchbase" %% "play2-couchbase" % "0.1-SNAPSHOT"
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    resolvers += "ancelin" at "https://raw.github.com/mathieuancelin/play2-couchbase/master/repository/snapshots",
    resolvers += "Spy Repository" at "http://files.couchbase.com/maven2"
  )
}
```

then create a `conf/play.plugins` file and add :

`400:org.ancelin.play2.couchbase.CouchbasePlugin`

add in your `conf/application.conf` file :

```

couchbase-ec {
  timeout=1000
  pollfutures=true
  polldelay=5
  execution-context {
    fork-join-executor {
      parallelism-factor = 20.0
      parallelism-max = 200
    }
  }
}

couchbase = [{
    host="127.0.0.1"
    port="8091"
    base="pools"
    bucket="bucketname"
    pass=""
    timeout="0"
}]

```

then you will be able to use the couchbase API from your Play controllers. The following code is asynchronous and uses Play's `Async { ... }`API under the hood. As you will need an execution context for all those async calls, you can use `Couchbase.couchbaseExecutor` based on your `application.conf` file. You can of course use Play default Execution Context (through `import play.api.libs.concurrent.Execution.Implicits._`) or your own.

```scala

import play.api.mvc.{Action, Controller}
import play.api.libs.json._
import org.ancelin.play2.couchbase.Couchbase._
import org.ancelin.play2.couchbase.Couchbase
import org.ancelin.play2.couchbase.CouchbaseController
import play.api.Play.current

case class User(name: String, surname: String, email: String)

object UserController extends Controller with CouchbaseController {

  implicit val couchbaseExecutionContext = Couchbase.couchbaseExecutor
  implicit val userReader = Json.reads[User]

  def getUser(key: String) = CouchbaseAction { implicit couchbaseclient =>
    get[User](key).map { maybeUser =>
      maybeUser.map(user => Ok(views.html.user(user)).getOrElse(BadRequest(s"Unable to find user with key: $key"))
    }
  }
}

```

this code is a shortcut for 

```scala

import play.api.mvc.{Action, Controller}
import play.api.libs.json._
import org.ancelin.play2.couchbase.Couchbase._
import org.ancelin.play2.couchbase.Couchbase
import play.api.Play.current

case class User(name: String, surname: String, email: String)

object UserController extends Controller {

  implicit val couchbaseExecutionContext = Couchbase.couchbaseExecutor
  implicit val userReader = Json.reads[User]

  def getUser(key: String) = Action { 
    Async {
      withCouchbase { implicit couchbaseclient =>
        get[User](key).map { maybeUser =>
          maybeUser.map(user => Ok(views.html.user(user)).getOrElse(BadRequest(s"Unable to find user with key: $key"))
        }
      }
    }
  }
}

```

You can of course connect many buckets with :

```

couchbase = [{
    host="127.0.0.1"
    port="8091"
    base="pools"
    bucket="bucket1"
    pass=""
    timeout="0"
}, {
   host="127.0.0.1"
   port="8091"
   base="pools"
   bucket="bucket2"
   pass=""
   timeout="0"
}, {
   host="192.168.0.42"
   port="8091"
   base="pools"
   bucket="bucket3"
   pass=""
   timeout="0"
}]

```

then select one of them for each of your operation :

```scala

object UserController extends Controller with CouchbaseController {

  implicit val couchbaseExecutionContext = Couchbase.couchbaseExecutor
  implicit val userReader = Json.reads[User]
  implicit val beerReader = Json.reads[Beer]

  def getUser(key: String) = CouchbaseAction("bucket1") { implicit couchbaseclient =>
    get[User](key).map { maybeUser =>
      maybeUser.map(user => Ok(views.html.user(user)).getOrElse(BadRequest(s"Unable to find user with key: $key"))
    }
  }

  def getBeer(key: String) = CouchbaseAction("bucket2") { request => implicit couchbaseclient =>
    get[Beer](key).map { maybeBeer =>
      maybeBeer.map(beer => Ok(views.html.beer(beer)).getOrElse(BadRequest(s"Unable to find beer with key: $key"))
    }
  }
}

```

or from inside a model :

```scala

import play.api.libs.json._
import org.ancelin.play2.couchbase.Couchbase._
import org.ancelin.play2.couchbase.Couchbase
import play.api.Play.current

case class Beer(id: String, name: String, brewery: String) {
  def save(): Future[OperationStatus] = Beer.save(this)
  def remove(): Future[OperationStatus] = Beer.remove(this)
}

object Beer {

  implicit val beerReader = Json.reads[Beer]
  implicit val beerWriter = Json.writes[Beer]
  implicit val ec = Couchbase.couchbaseExecutor
  implicit val client = Couchbase.client("bucket2")

  def findById(id: String): Future[Option[Beer]] = {
    get[Beer](id)
  }

  def findAll(): Future[List[Beer]] = {
    find[Beer]("beer", "by_name")(new Query().setIncludeDocs(true).setStale(Stale.FALSE))
  }

  def findByName(name: String): Future[Option[Beer]] = {
    val query = new Query().setIncludeDocs(true).setLimit(1)
          .setRangeStart(ComplexKey.of(name))
          .setRangeEnd(ComplexKey.of(s"$name\uefff").setStale(Stale.FALSE))
    find[Beer]("beer", "by_name")(query).map(_.headOption)
  }

  def save(beer: Beer): Future[OperationStatus] = {
    set[Beer](beer)
  }

  def remove(beer: Beer): Future[OperationStatus] = {
    delete[Beer](beer)
  }
}

```

You can also access Couchbase from your Java application :

```java

package controllers;

import models.ShortURL;
import org.ancelin.play2.java.couchbase.Couchbase;
import org.ancelin.play2.java.couchbase.CouchbaseAPI;
import play.libs.F;
import static play.libs.F.*;
import play.mvc.Controller;
import play.mvc.Result;

public class Application extends Controller {

    public static CouchbaseAPI collection = Couchbase.bucket("bucket1");

    public static Result  getUser(final String key) {
        return async(
            collection.get(key, User.class).map(new Function<User, Result>() {
                @Override
                public Result apply(User user) throws Throwable {
                    if (user == null) {
                        return badRequest("Unable to find user with key: " + key);
                    }
                    return ok(views.html.user.render(user));
                }
            })
        );
    }
}

```

or from model

```java

package models;

import com.couchbase.client.protocol.views.ComplexKey;
import com.couchbase.client.protocol.views.Query;
import com.couchbase.client.protocol.views.Stale;
import net.spy.memcached.ops.OperationStatus;
import org.ancelin.play2.java.couchbase.Couchbase;
import org.ancelin.play2.java.couchbase.CouchbaseAPI;
import play.libs.F;
import static play.libs.F.*;

import java.util.Collection;

public class ShortURL {

    public String id;
    public String originalUrl;

    public ShortURL() {}

    public ShortURL(String id, String originalUrl) {
        this.id = id;
        this.originalUrl = originalUrl;
    }

    public static CouchbaseAPI collection = Couchbase.bucket("default");

    public static Promise<ShortURL> findById(String id) {
        return collection.get(id, ShortURL.class);
    }

    public static Promise<Collection<ShortURL>> findAll() {
        return collection.find("shorturls", "by_url",
            new Query().setIncludeDocs(true).setStale(Stale.FALSE), ShortURL.class);
    }

    public static Promise<Option<ShortURL>> findByURL(String url) {
        Query query = new Query()
                .setLimit(1)
                .setIncludeDocs(true)
                .setStale(Stale.FALSE)
                .setRangeStart(ComplexKey.of(url))
                .setRangeEnd(ComplexKey.of(url + "\uefff"));
        return collection.find("shorturls", "by_url", query, ShortURL.class)
                .map(new Function<Collection<ShortURL>, Option<ShortURL>>() {
            @Override
            public Option<ShortURL> apply(Collection<ShortURL> shortURLs) throws Throwable {
                if (shortURLs.isEmpty()) {
                    return Option.None();
                }
                return Option.Some(shortURLs.iterator().next());
            }
        });
    }

    public static Promise<OperationStatus> save(ShortURL url) {
        return collection.set(url.id, url);
    }

    public static Promise<OperationStatus> remove(ShortURL url) {
        return collection.delete(url.id);
    }
}

```

If you want to clone this git repo, as we embed snapshot libs (maybe we will move it later), it can be useful to use

`git clone --depth xxx git://github.com/mathieuancelin/play2-couchbase.git`