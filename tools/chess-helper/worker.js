/*
 * Web Worker wrapper around the engine, so analysis never blocks the UI.
 * Only used when the page is served over http(s); opening index.html straight
 * from disk falls back to an in-page searcher (see index.html).
 */
'use strict';

importScripts('engine.js');

var E = self.ChessEngine;
var searcher = new E.Searcher(21);

self.onmessage = function (event) {
  var msg = event.data || {};

  if (msg.type === 'reset') {
    searcher.clearTable();
    return;
  }

  if (msg.type !== 'analyze') return;

  var pos;
  try {
    pos = new E.Position(msg.fen);
  } catch (err) {
    self.postMessage({ type: 'error', message: String(err.message || err) });
    return;
  }

  var result = searcher.search(pos, {
    timeMs: msg.timeMs,
    maxDepth: msg.maxDepth,
    onIteration: function (info) {
      self.postMessage({ type: 'info', id: msg.id, info: strip(info) });
    }
  });

  self.postMessage({ type: 'result', id: msg.id, info: strip(result) });
};

// Moves are packed ints that mean nothing outside their own position, so only
// the display-ready fields cross the worker boundary.
function strip(info) {
  return {
    uci: info.uci,
    san: info.san,
    score: info.score,
    mate: info.mate,
    depth: info.depth,
    nodes: info.nodes,
    timeMs: info.timeMs,
    pvSan: info.pvSan
  };
}
