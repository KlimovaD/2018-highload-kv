# Loadtest

## Scripts

### get.lua

```lua
id = 0
wrk.method = "GET"

request = function()
    local path = "/v0/entity?id=" .. id
    id = id + 1
    return wrk.format(nil, path)
end
```

### put.lua

```lua
id = 0

wrk.method = "PUT"
wrk.body = "p}sn42#4PfX5%oq42dMoAyncNv8%E2kD}lSJ1Oj~x*0Vfx#s5*"

request = function()
    local path = "/v0/entity?id=" .. id
    id = id + 1
    return wrk.format(nil, path)
end
```

### delete.lua

```lua
id = 0
wrk.method = "DELETE"

request = function()
    local path = "/v0/entity?id=" .. id
    id = id + 1
    return wrk.format(nil, path)
end
```

## `wrk` profiling

### `PUT` 
* 2 minutes
* 1 thread
* 1 connection
```
wrk --latency -c1 -t1 -d2m -s put.lua http://localhost:8080
Running 2m test @ http://localhost:8080
  1 threads and 1 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   642.46us    4.83ms  97.46ms   98.73%
    Req/Sec     6.59k     1.19k    7.51k    87.58%
  Latency Distribution
     50%  127.00us
     75%  136.00us
     90%  174.00us
     99%   18.16ms
  787319 requests in 2.00m, 50.31MB read
Requests/sec:   6558.63
Transfer/sec:    429.13KB
```

* 2 minutes
* 2 threads
* 2 connections
```
wrk --latency -c2 -t2 -d2m -s put.lua http://localhost:8080
Running 2m test @ http://localhost:8080
  2 threads and 2 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   735.22us    4.60ms  96.57ms   98.69%
    Req/Sec     3.69k   454.46     4.07k    92.29%
  Latency Distribution
     50%  244.00us
     75%  257.00us
     90%  291.00us
     99%   18.62ms
  880359 requests in 2.00m, 56.25MB read
Requests/sec:   7333.78
Transfer/sec:    479.85KB
```

* 2 minutes
* 4 threads
* 4 connections
```
wrk --latency -c4 -t4 -d2m -s put.lua http://localhost:8080
Running 2m test @ http://localhost:8080
  4 threads and 4 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.26ms    6.61ms 107.14ms   97.88%
    Req/Sec     2.58k     0.96k    3.91k    57.83%
  Latency Distribution
     50%  275.00us
     75%  506.00us
     90%  584.00us
     99%   42.08ms
  1230642 requests in 2.00m, 78.63MB read
Requests/sec:  10249.49
Transfer/sec:    670.62KB
```

### `GET` 
* 2 minutes
* 1 thread
* 1 connection
```
wrk --latency -c1 -t1 -d2m -s get.lua http://localhost:8080
Running 2m test @ http://localhost:8080
  1 threads and 1 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   769.04us    5.14ms 101.99ms   98.26%
    Req/Sec     7.24k     0.90k    8.25k    86.67%
  Latency Distribution
     50%  117.00us
     75%  131.00us
     90%  152.00us
     99%   30.07ms
  864587 requests in 2.00m, 56.15MB read
  Non-2xx or 3xx responses: 81029
Requests/sec:   7200.66
Transfer/sec:    478.83KB
```
* 2 minutes
* 2 threads
* 2 connections
```
wrk --latency -c2 -t2 -d2m -s get.lua http://localhost:8080
Running 2m test @ http://localhost:8080
  2 threads and 2 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   775.84us    5.04ms  99.07ms   98.39%
    Req/Sec     5.37k   837.72     5.92k    93.17%
  Latency Distribution
     50%  162.00us
     75%  169.00us
     90%  196.00us
     99%   27.90ms
  1282517 requests in 2.00m, 83.17MB read
Requests/sec:  10682.63
Transfer/sec:    709.39KB
```
* 2 minutes
* 4 threads
* 4 connections
```
wrk --latency -c4 -t4 -d2m -s get.lua http://localhost:8080
Running 2m test @ http://localhost:8080
  4 threads and 4 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   746.35us    4.61ms  87.34ms   98.53%
    Req/Sec     4.22k   484.74     4.79k    89.48%
  Latency Distribution
     50%  219.00us
     75%  255.00us
     90%  291.00us
     99%   23.86ms
  2016213 requests in 2.00m, 130.75MB read
Requests/sec:  16792.57
Transfer/sec:      1.09MB
```

### `DELETE`
* 1 minute
* 4 threads
* 4 connections
```
wrk --latency -c4 -t4 -d1m -s delete.lua http://localhost:8080
Running 1m test @ http://localhost:8080
  4 threads and 4 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.24ms    6.92ms 107.00ms   97.45%
    Req/Sec     4.70k   723.69     5.43k    92.37%
  Latency Distribution
     50%  178.00us
     75%  196.00us
     90%  288.00us
     99%   45.50ms
  1121557 requests in 1.00m, 72.73MB read
Requests/sec:  18681.82
Transfer/sec:      1.21MB
```

## Results

```
+--------------------------+-------+-------+
|        2 minutes         |  PUT  |  GET  |
+--------------------------+-------+-------+
| 1 thread, 1 connection   | 787k  | 864k  |
| 2 threads, 2 connections | 880k  | 1282k |
| 4 threads, 4 connections | 1230k | 2016k |
+--------------------------+-------+-------+
```
