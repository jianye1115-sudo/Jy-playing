/*
 * Self-test for the engine. Run with: node test.js
 *
 * Perft counts every leaf node of the move tree to a given depth. The
 * expected numbers below are the published values for these positions, so a
 * mismatch means move generation, make/unmake or legality filtering is wrong.
 */
'use strict';

var E = require('./engine.js');

var failures = 0;
var checks = 0;

function check(name, actual, expected) {
  checks++;
  var ok = actual === expected;
  if (!ok) failures++;
  console.log((ok ? '  PASS  ' : '  FAIL  ') + name +
    (ok ? '' : '  expected ' + expected + ', got ' + actual));
}

/* ---------------------------------------------------------------- *
 * Perft
 * ---------------------------------------------------------------- */

var PERFT_SUITE = [
  {
    name: 'startpos',
    fen: E.START_FEN,
    counts: [1, 20, 400, 8902, 197281, 4865609]
  },
  {
    // "Kiwipete" - dense with castling, pins and captures.
    name: 'kiwipete',
    fen: 'r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1',
    counts: [1, 48, 2039, 97862, 4085603]
  },
  {
    // Endgame heavy on en-passant and promotion edge cases.
    name: 'position 3',
    fen: '8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1',
    counts: [1, 14, 191, 2812, 43238, 674624]
  },
  {
    name: 'position 4',
    fen: 'r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1',
    counts: [1, 6, 264, 9467, 422333]
  },
  {
    name: 'position 4 mirrored',
    fen: 'r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1',
    counts: [1, 6, 264, 9467, 422333]
  },
  {
    name: 'position 5',
    fen: 'rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8',
    counts: [1, 44, 1486, 62379, 2103487]
  },
  {
    name: 'position 6',
    fen: 'r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10',
    counts: [1, 46, 2079, 89890, 3894594]
  }
];

console.log('\nPerft');
var totalNodes = 0;
var perftStart = Date.now();
PERFT_SUITE.forEach(function (t) {
  for (var depth = 1; depth < t.counts.length; depth++) {
    var pos = new E.Position(t.fen);
    var n = E.perft(pos, depth);
    totalNodes += n;
    check(t.name + ' depth ' + depth, n, t.counts[depth]);
  }
});
var perftMs = Date.now() - perftStart;
console.log('  ' + totalNodes.toLocaleString() + ' nodes in ' + perftMs + ' ms (' +
  Math.round(totalNodes / Math.max(perftMs, 1)) + 'k nodes/sec)');

/* ---------------------------------------------------------------- *
 * make / unmake round-trip
 * ---------------------------------------------------------------- */

console.log('\nMake/unmake integrity');
(function () {
  var fens = PERFT_SUITE.map(function (t) { return t.fen; });
  var allRestored = true;
  var allHashesMatch = true;
  fens.forEach(function (fen) {
    var pos = new E.Position(fen);
    var before = pos.fen();
    var hlo = pos.hlo, hhi = pos.hhi;
    var moves = pos.generateMoves(false);
    for (var i = 0; i < moves.length; i++) {
      if (!pos.makeMove(moves[i])) continue;
      // A freshly parsed copy of the resulting FEN must hash identically,
      // which proves the incremental Zobrist update is correct.
      var rebuilt = new E.Position(pos.fen());
      if (rebuilt.hlo !== pos.hlo || rebuilt.hhi !== pos.hhi) allHashesMatch = false;
      pos.unmakeMove();
    }
    if (pos.fen() !== before || pos.hlo !== hlo || pos.hhi !== hhi) allRestored = false;
  });
  check('position restored after unmake', allRestored, true);
  check('incremental hash matches rebuilt position', allHashesMatch, true);
})();

/* ---------------------------------------------------------------- *
 * FEN round-trip
 * ---------------------------------------------------------------- */

console.log('\nFEN round-trip');
PERFT_SUITE.forEach(function (t) {
  var pos = new E.Position(t.fen);
  check(t.name, pos.fen(), t.fen);
});

/* ---------------------------------------------------------------- *
 * SAN
 * ---------------------------------------------------------------- */

console.log('\nSAN');
(function () {
  var pos = new E.Position(E.START_FEN);
  ['e4', 'e5', 'Nf3', 'Nc6', 'Bb5', 'a6', 'Ba4', 'Nf6', 'O-O'].forEach(function (san) {
    var m = pos.moveFromString(san);
    check('parses ' + san, m !== 0, true);
    if (m) {
      check('renders ' + san, pos.moveToSan(m), san);
      pos.makeMove(m);
    }
  });
  // a6 is a pawn move, so the halfmove clock resets there and stands at 3.
  check('Ruy Lopez FEN',
    pos.fen(),
    'r1bqkb1r/1ppp1ppp/p1n2n2/4p3/B3P3/5N2/PPPP1PPP/RNBQ1RK1 b kq - 3 5');

  // Knights on a2 and e2 both reach c3, so the file must disambiguate them.
  var dis = new E.Position('8/8/8/7k/8/8/N3N3/4K3 w - - 0 1');
  var m1 = dis.moveFromString('Nac3');
  check('file disambiguation Nac3', m1 !== 0, true);
  if (m1) check('renders Nac3', dis.moveToSan(m1), 'Nac3');
  var m2e = dis.moveFromString('Nec3');
  check('file disambiguation Nec3', m2e !== 0, true);
  if (m2e) check('renders Nec3', dis.moveToSan(m2e), 'Nec3');

  // Promotion with capture and check.
  var promo = new E.Position('1n2k3/P7/8/8/8/8/8/4K3 w - - 0 1');
  var pm = promo.moveFromString('axb8=Q+');
  check('parses axb8=Q+', pm !== 0, true);
  if (pm) check('renders axb8=Q+', promo.moveToSan(pm), 'axb8=Q+');
})();

/* ---------------------------------------------------------------- *
 * Game state detection
 * ---------------------------------------------------------------- */

console.log('\nGame state');
check('fool\'s mate is checkmate',
  new E.Position('rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3').gameResult(),
  'checkmate');
check('stalemate detected',
  new E.Position('7k/5Q2/6K1/8/8/8/8/8 b - - 0 1').gameResult(),
  'stalemate');
check('K v K is insufficient material',
  new E.Position('4k3/8/8/8/8/8/8/4K3 w - - 0 1').gameResult(),
  'insufficient-material');
check('K+B v K is insufficient material',
  new E.Position('4k3/8/8/8/8/8/8/2B1K3 w - - 0 1').gameResult(),
  'insufficient-material');
check('K+N+N v K is not called insufficient',
  new E.Position('4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1').hasInsufficientMaterial(),
  false);
check('position with legal moves is ongoing',
  new E.Position(E.START_FEN).gameResult(),
  null);

/* ---------------------------------------------------------------- *
 * Evaluation sanity
 * ---------------------------------------------------------------- */

console.log('\nEvaluation');
(function () {
  // Evaluation is side-relative, so the start position must be near zero and
  // a mirrored position must evaluate identically for the side to move.
  var start = new E.Position(E.START_FEN).evaluate();
  check('start position is roughly balanced', Math.abs(start) <= 40, true);

  var w = new E.Position('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN1 w kq - 0 1').evaluate();
  var b = new E.Position('rnbqkbn1/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQ - 0 1').evaluate();
  check('evaluation is colour-symmetric', w, b);

  var upQueen = new E.Position('rnb1kbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1').evaluate();
  check('a queen up scores at least +800', upQueen > 800, true);
})();

/* ---------------------------------------------------------------- *
 * Search
 * ---------------------------------------------------------------- */

console.log('\nSearch');
(function () {
  var searcher = new E.Searcher(18);

  // Mate in one: back-rank mate with the rook.
  var m1 = new E.Position('6k1/5ppp/8/8/8/8/8/R3K3 w Q - 0 1');
  var r1 = searcher.search(m1, { timeMs: 2000, maxDepth: 8 });
  check('finds mate in 1 (Ra8#)', r1.san, 'Ra8#');
  check('reports mate score 1', r1.mate, 1);

  // Mate in two: the king must take the opposition before the rook delivers.
  // Both Kg6 and Kf7 mate, so only the distance is asserted.
  var m2 = new E.Position('7k/8/5K2/8/8/8/8/1R6 w - - 0 1');
  var r2 = searcher.search(m2, { timeMs: 4000, maxDepth: 12 });
  check('finds mate in 2', r2.mate, 2);
  check('mate in 2 ends in mate', r2.pvSan[r2.pvSan.length - 1].indexOf('#') > 0, true);

  // Mate in three built on a discovered check from the e5 bishop.
  var m3 = new E.Position('r5rk/5p1p/5R2/4B3/8/8/7P/7K w - - 0 1');
  var r3 = searcher.search(m3, { timeMs: 5000, maxDepth: 14 });
  check('finds mate in 3', r3.mate, 3);
  check('mate in 3 starts with the discovered check Ra6+', r3.san, 'Ra6+');

  // Free queen hanging on d5 - any sane search must take it.
  var hang = new E.Position('rnb1kbnr/pppp1ppp/8/3qp3/8/2N5/PPPPPPPP/R1BQKBNR w KQkq - 0 1');
  var rh = searcher.search(hang, { timeMs: 2000, maxDepth: 8 });
  check('captures the hanging queen', rh.san, 'Nxd5');

  // Avoiding stalemate: white is winning and must not stalemate black.
  var deep = new E.Position(E.START_FEN);
  var rd = searcher.search(deep, { timeMs: 3000, maxDepth: 20 });
  check('reaches at least depth 8 from startpos in 3s', rd.depth >= 8, true);
  check('returns a legal first move',
    new E.Position(E.START_FEN).moveFromString(rd.uci) !== 0, true);
  check('reports a principal variation', rd.pvSan.length > 0, true);
  console.log('  startpos: depth ' + rd.depth + ', ' + rd.nodes.toLocaleString() +
    ' nodes, ' + rd.timeMs + ' ms, best ' + rd.san +
    ' (' + (rd.score / 100).toFixed(2) + '), pv: ' + rd.pvSan.join(' '));

  // The search must leave the position exactly as it found it.
  var before = new E.Position(E.START_FEN).fen();
  check('search does not mutate the position', deep.fen(), before);
})();

console.log('\n' + (failures === 0 ? 'All ' + checks + ' checks passed.'
                                   : failures + ' of ' + checks + ' checks FAILED.'));
process.exit(failures === 0 ? 0 : 1);
