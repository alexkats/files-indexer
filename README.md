## Files indexer

This project allows to track different paths on the filesystem
and index files there. Indexing is performed per word, and
different splitting mechanisms are possible. Indexing is performed
in parallel, and it's possible to query the partially built index.

To see it in action, you can run the indexer as a REPL program.
You can use `help` command to see the usage and different commands.
The program supports autocompletion of commands and their arguments.
The log is written to `stderr` by default, so it's recommended to
redirect it to a separate file to have a cleaner output.

To make it a bit more convenient, I created a `run.sh` script
that runs the `files-indexer.jar` jar (unless there's a presumably
more fresh version in the target directory). It also redirects
`stderr` to a `files-indexer.log` file.

![Demo](demo.gif)

### Limitations

* It's not possible to track paths that don't exist yet.
* When a tracked path is deleted from the filesystem,
  it will be stopped being tracked implicitly and will
  not be tracked even if recreated.
* Tracking symbolic links (explicitly or through watching a parent
  path) is not supported. It's quite challenging to track changes
  in the link itself (especially if it's a file). Also, if the
  target for link changes, the library for watching won't
  recognize this change.
* Basically we index only regular files and directories. So it's
  explicitly forbidden to index file descriptors and devices
  (e.g. `/dev/fd/0` or `/dev/urandom`).
* It's explicitly forbidden to start/stop watching directories
  in parallel. Those calls are chained in a queue that invokes such
  calls one by one. The reason behind this is that there are
  multiple state changes happening with directory watchers, and some
  weird side effects would be possible if parallel start/stop was
  possible. Please note, that this doesn't block the indexing, and
  you can still request index results, basically the only thing
  that's done sequential is registering an event of starting/stopping
  watching
* When we try to start watching a path that is a child to some
  already tracked path, nothing would happen.
* At the same time, if we try to start watching a path that is
  a parent to some already tracked paths, those paths wouldn't
  longer be tracked explicitly. Only parent path would be tracked
  explicitly in this case. The side effect of it is that if we
  stop tracking that parent path, child paths won't be tracked
  anymore as well.
* Several filters are added to increase performance of indexing
  large directories. One of those is binary file filtering. It
  first tries to find UTF BOMs, and if none are found, 1KB is read
  to find if there are any null bytes present. If no BOMs are found
  and there's at least one null byte found, the file is considered
  binary.
* One of the side effect of filtering binary files is that archives
  are not indexed anymore.
* It's assumed that all files that are indexed are in the
  UTF-8 encoding.

### Possible enhancements in the future

* Make it possible to read contents of an archive (recursively)
  and apply the same binary filter on files inside and index
  non-binary files inside the archive.
* It would be great to give a priority to events that delete
  index if the path is stopped being watched. That would be helpful
  in situations when we index some very large directory and want
  to stop watching it without too much waiting.
* Register some paths for tracking that don't yet exist on the
  filesystem.
