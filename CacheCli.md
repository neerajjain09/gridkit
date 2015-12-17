# Introduction #

Main goal of CacheCli is to be a tool which can do simple cache operation without complex configuration. In particular, it doesn't require application specific JARs or `cache-config.xml`.

# Usage #

Here is command to print size of cache.
```
java -cp coherence.jar;cache-cli.jar CacheCli -c extend://server.com:9099/MyCache size
```

Next command will list content of remote cache parsing POF structure
```
java -cp coherence.jar;cache-cli.jar CacheCli -c extend://server.com:9099/MyCache list -pp -s entries
```

We cannot show object, because tool may not have all classes, but displaying POF properties make a lot of sense for troubleshooting.

Another command will dump all cache content in ZIP archive
```
java -cp coherence.jar;cache-cli.jar CacheCli -c extend://server.com:9099/MyCache export -zf MyCache.zip
```

Alternatively you can export just a subset of partitions
```
java -cp coherence.jar;cache-cli.jar CacheCli -c extend://server.com:9099/MyCache export -p 0-127 -zf MyCache_0_127.zip
```

This is useful if you want to do dump in parallel from several boxes.

An of cause we can upload that dump back to a cache
```
java -cp coherence.jar;cache-cli.jar CacheCli -c extend://server.com:9099/MyCache import -zf MyCache.zip
```


## Important ##
  * Only Coherence\*Extend with POF codec is supported
  * You should use platform specific separator in `-cp` option
  * `coherence.jar` - path to your Oracle Coherence JAR
  * `cache-cli.jar` - path to CacheCli JAR
  * `coherence.jar` version should match cluster version

## Why not CohQL CLI? ##
[CohQL CLI tool](http://docs.oracle.com/cd/E18686_01/coh.37/e18677/api_cq.htm) is quite capable, but it requires complex setup to work with particular cluster. CacheCli is designed to be as application agnostic as possible.

# Project details #

Download: TODO

Checkout: svn co https://gridkit.googlecode.com/svn/coherence-tools/trunk/cache-cli

Browse: http://code.google.com/p/gridkit/source/browse/coherence-tools/trunk/cache-cli/