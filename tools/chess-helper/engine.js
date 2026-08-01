/*
 * A self-contained chess engine: board representation, legal move generation,
 * evaluation and an alpha-beta search. No dependencies.
 *
 * Runs in a browser (as a classic <script>), in a Web Worker (importScripts)
 * and in Node (require) so the perft suite can exercise the same code.
 */
;(function (root, factory) {
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.ChessEngine = api;
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  /* ------------------------------------------------------------------ *
   * Board representation
   *
   * Squares use the 0x88 layout: sq = rank * 16 + file, with a1 = 0 and
   * h8 = 119. A square is off the board iff (sq & 0x88) is non-zero, which
   * makes off-board detection a single mask during move generation.
   * ------------------------------------------------------------------ */

  var WHITE = 0, BLACK = 1;
  var EMPTY = 0, PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6;

  // A piece is `type | (color << 3)`, so white is 1..6 and black is 9..14.
  function pieceOf(color, type) { return type | (color << 3); }
  function colorOf(pc) { return pc >> 3; }
  function typeOf(pc) { return pc & 7; }

  var PIECE_TO_CHAR = [];
  PIECE_TO_CHAR[pieceOf(WHITE, PAWN)] = 'P';
  PIECE_TO_CHAR[pieceOf(WHITE, KNIGHT)] = 'N';
  PIECE_TO_CHAR[pieceOf(WHITE, BISHOP)] = 'B';
  PIECE_TO_CHAR[pieceOf(WHITE, ROOK)] = 'R';
  PIECE_TO_CHAR[pieceOf(WHITE, QUEEN)] = 'Q';
  PIECE_TO_CHAR[pieceOf(WHITE, KING)] = 'K';
  PIECE_TO_CHAR[pieceOf(BLACK, PAWN)] = 'p';
  PIECE_TO_CHAR[pieceOf(BLACK, KNIGHT)] = 'n';
  PIECE_TO_CHAR[pieceOf(BLACK, BISHOP)] = 'b';
  PIECE_TO_CHAR[pieceOf(BLACK, ROOK)] = 'r';
  PIECE_TO_CHAR[pieceOf(BLACK, QUEEN)] = 'q';
  PIECE_TO_CHAR[pieceOf(BLACK, KING)] = 'k';

  var CHAR_TO_PIECE = {};
  for (var pi = 0; pi < PIECE_TO_CHAR.length; pi++) {
    if (PIECE_TO_CHAR[pi]) CHAR_TO_PIECE[PIECE_TO_CHAR[pi]] = pi;
  }

  var KNIGHT_OFFS = [-33, -31, -18, -14, 14, 18, 31, 33];
  var BISHOP_OFFS = [-17, -15, 15, 17];
  var ROOK_OFFS = [-16, -1, 1, 16];
  var KING_OFFS = [-17, -16, -15, -1, 1, 15, 16, 17];

  // Castling rights are a 4-bit mask.
  var CASTLE_WK = 1, CASTLE_WQ = 2, CASTLE_BK = 4, CASTLE_BQ = 8;

  var SQ_A1 = 0, SQ_E1 = 4, SQ_H1 = 7;
  var SQ_A8 = 112, SQ_E8 = 116, SQ_H8 = 119;

  // Moving from or to one of these squares clears the corresponding rights.
  var CASTLE_MASK = new Int32Array(128);
  for (var cm = 0; cm < 128; cm++) CASTLE_MASK[cm] = 15;
  CASTLE_MASK[SQ_E1] = 15 & ~(CASTLE_WK | CASTLE_WQ);
  CASTLE_MASK[SQ_H1] = 15 & ~CASTLE_WK;
  CASTLE_MASK[SQ_A1] = 15 & ~CASTLE_WQ;
  CASTLE_MASK[SQ_E8] = 15 & ~(CASTLE_BK | CASTLE_BQ);
  CASTLE_MASK[SQ_H8] = 15 & ~CASTLE_BK;
  CASTLE_MASK[SQ_A8] = 15 & ~CASTLE_BQ;

  function onBoard(sq) { return (sq & 0x88) === 0; }
  function fileOf(sq) { return sq & 7; }
  function rankOf(sq) { return sq >> 4; }
  function to64(sq) { return (sq >> 4) * 8 + (sq & 7); }

  function squareName(sq) {
    return 'abcdefgh'.charAt(fileOf(sq)) + (rankOf(sq) + 1);
  }

  function squareFromName(name) {
    if (!name || name.length < 2) return -1;
    var f = name.charCodeAt(0) - 97;
    var r = name.charCodeAt(1) - 49;
    if (f < 0 || f > 7 || r < 0 || r > 7) return -1;
    return r * 16 + f;
  }

  /* ------------------------------------------------------------------ *
   * Move encoding
   *
   * Packed into one 32-bit integer so move lists stay in typed arrays and
   * generate no garbage during search.
   * ------------------------------------------------------------------ */

  var FLAG_EP = 1, FLAG_CASTLE = 2, FLAG_PAWN2 = 4;

  function encodeMove(from, to, captured, promo, flags) {
    return from | (to << 8) | (captured << 16) | (promo << 20) | (flags << 24);
  }
  function moveFrom(m) { return m & 0xff; }
  function moveTo(m) { return (m >> 8) & 0xff; }
  function moveCaptured(m) { return (m >> 16) & 0xf; }
  function movePromo(m) { return (m >> 20) & 0xf; }
  function moveFlags(m) { return (m >> 24) & 0xf; }

  function moveToUci(m) {
    var s = squareName(moveFrom(m)) + squareName(moveTo(m));
    var p = movePromo(m);
    if (p) s += PIECE_TO_CHAR[pieceOf(BLACK, p)];
    return s;
  }

  /* ------------------------------------------------------------------ *
   * Zobrist hashing
   * ------------------------------------------------------------------ */

  // A fixed seed keeps hashes reproducible between runs, which makes
  // debugging transposition-table behaviour far easier.
  var rngState = 0x9e3779b9;
  function nextRandom() {
    rngState ^= rngState << 13; rngState |= 0;
    rngState ^= rngState >>> 17;
    rngState ^= rngState << 5; rngState |= 0;
    return rngState;
  }

  var ZP_LO = new Int32Array(15 * 128), ZP_HI = new Int32Array(15 * 128);
  for (var zi = 0; zi < 15 * 128; zi++) { ZP_LO[zi] = nextRandom(); ZP_HI[zi] = nextRandom(); }
  var ZSIDE_LO = nextRandom(), ZSIDE_HI = nextRandom();
  var ZCASTLE_LO = new Int32Array(16), ZCASTLE_HI = new Int32Array(16);
  for (var zc = 0; zc < 16; zc++) { ZCASTLE_LO[zc] = nextRandom(); ZCASTLE_HI[zc] = nextRandom(); }
  var ZEP_LO = new Int32Array(8), ZEP_HI = new Int32Array(8);
  for (var ze = 0; ze < 8; ze++) { ZEP_LO[ze] = nextRandom(); ZEP_HI[ze] = nextRandom(); }

  var START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

  /* ------------------------------------------------------------------ *
   * Position
   * ------------------------------------------------------------------ */

  function Position(fen) {
    this.board = new Int8Array(128);
    // Piece lists, so move generation walks only occupied squares.
    this.plist = [new Int32Array(16), new Int32Array(16)];
    this.pcount = [0, 0];
    this.pidx = new Int32Array(128);
    this.kingSq = [-1, -1];
    this.side = WHITE;
    this.castling = 0;
    this.ep = -1;
    this.halfmove = 0;
    this.fullmove = 1;
    this.hlo = 0;
    this.hhi = 0;
    this.undoStack = [];
    this.repHist = [];
    this.setFen(fen || START_FEN);
  }

  Position.prototype.clear = function () {
    this.board.fill(0);
    this.pcount[0] = 0; this.pcount[1] = 0;
    this.kingSq[0] = -1; this.kingSq[1] = -1;
    this.side = WHITE;
    this.castling = 0;
    this.ep = -1;
    this.halfmove = 0;
    this.fullmove = 1;
    this.hlo = 0; this.hhi = 0;
    this.undoStack.length = 0;
    this.repHist.length = 0;
  };

  Position.prototype.addPiece = function (sq, pc) {
    var c = colorOf(pc);
    this.board[sq] = pc;
    this.pidx[sq] = this.pcount[c];
    this.plist[c][this.pcount[c]++] = sq;
    var z = pc * 128 + sq;
    this.hlo ^= ZP_LO[z]; this.hhi ^= ZP_HI[z];
    if (typeOf(pc) === KING) this.kingSq[c] = sq;
  };

  Position.prototype.removePiece = function (sq) {
    var pc = this.board[sq];
    var c = colorOf(pc);
    var i = this.pidx[sq];
    var last = --this.pcount[c];
    var lastSq = this.plist[c][last];
    this.plist[c][i] = lastSq;
    this.pidx[lastSq] = i;
    this.board[sq] = 0;
    var z = pc * 128 + sq;
    this.hlo ^= ZP_LO[z]; this.hhi ^= ZP_HI[z];
  };

  Position.prototype.movePiece = function (from, to) {
    var pc = this.board[from];
    var c = colorOf(pc);
    var i = this.pidx[from];
    this.plist[c][i] = to;
    this.pidx[to] = i;
    this.board[to] = pc;
    this.board[from] = 0;
    var zf = pc * 128 + from, zt = pc * 128 + to;
    this.hlo ^= ZP_LO[zf] ^ ZP_LO[zt];
    this.hhi ^= ZP_HI[zf] ^ ZP_HI[zt];
    if (typeOf(pc) === KING) this.kingSq[c] = to;
  };

  Position.prototype.setFen = function (fen) {
    this.clear();
    var parts = String(fen).trim().split(/\s+/);
    var rows = parts[0].split('/');
    if (rows.length !== 8) throw new Error('Invalid FEN: expected 8 ranks');
    for (var r = 0; r < 8; r++) {
      var row = rows[r];
      var rank = 7 - r;
      var file = 0;
      for (var i = 0; i < row.length; i++) {
        var ch = row.charAt(i);
        if (ch >= '1' && ch <= '8') {
          file += ch.charCodeAt(0) - 48;
        } else {
          var pc = CHAR_TO_PIECE[ch];
          if (!pc) throw new Error('Invalid FEN: unknown piece "' + ch + '"');
          if (file > 7) throw new Error('Invalid FEN: rank too long');
          this.addPiece(rank * 16 + file, pc);
          file++;
        }
      }
      if (file !== 8) throw new Error('Invalid FEN: rank ' + (rank + 1) + ' has ' + file + ' files');
    }
    this.side = (parts[1] === 'b') ? BLACK : WHITE;
    var cast = parts[2] || '-';
    if (cast.indexOf('K') >= 0) this.castling |= CASTLE_WK;
    if (cast.indexOf('Q') >= 0) this.castling |= CASTLE_WQ;
    if (cast.indexOf('k') >= 0) this.castling |= CASTLE_BK;
    if (cast.indexOf('q') >= 0) this.castling |= CASTLE_BQ;
    this.ep = (parts[3] && parts[3] !== '-') ? squareFromName(parts[3]) : -1;
    this.halfmove = parts[4] ? parseInt(parts[4], 10) : 0;
    this.fullmove = parts[5] ? parseInt(parts[5], 10) : 1;
    if (isNaN(this.halfmove)) this.halfmove = 0;
    if (isNaN(this.fullmove)) this.fullmove = 1;

    // Castling rights that the king/rook placement cannot support would
    // corrupt the search, so drop them rather than trusting the input.
    if (this.board[SQ_E1] !== pieceOf(WHITE, KING)) this.castling &= ~(CASTLE_WK | CASTLE_WQ);
    if (this.board[SQ_H1] !== pieceOf(WHITE, ROOK)) this.castling &= ~CASTLE_WK;
    if (this.board[SQ_A1] !== pieceOf(WHITE, ROOK)) this.castling &= ~CASTLE_WQ;
    if (this.board[SQ_E8] !== pieceOf(BLACK, KING)) this.castling &= ~(CASTLE_BK | CASTLE_BQ);
    if (this.board[SQ_H8] !== pieceOf(BLACK, ROOK)) this.castling &= ~CASTLE_BK;
    if (this.board[SQ_A8] !== pieceOf(BLACK, ROOK)) this.castling &= ~CASTLE_BQ;

    if (this.side === BLACK) { this.hlo ^= ZSIDE_LO; this.hhi ^= ZSIDE_HI; }
    this.hlo ^= ZCASTLE_LO[this.castling]; this.hhi ^= ZCASTLE_HI[this.castling];
    if (this.ep >= 0) {
      this.hlo ^= ZEP_LO[fileOf(this.ep)];
      this.hhi ^= ZEP_HI[fileOf(this.ep)];
    }
    this.repHist.push(this.hlo);
    return this;
  };

  Position.prototype.fen = function () {
    var out = '';
    for (var rank = 7; rank >= 0; rank--) {
      var empty = 0;
      for (var file = 0; file < 8; file++) {
        var pc = this.board[rank * 16 + file];
        if (pc === 0) { empty++; continue; }
        if (empty) { out += empty; empty = 0; }
        out += PIECE_TO_CHAR[pc];
      }
      if (empty) out += empty;
      if (rank > 0) out += '/';
    }
    out += this.side === WHITE ? ' w ' : ' b ';
    var cast = '';
    if (this.castling & CASTLE_WK) cast += 'K';
    if (this.castling & CASTLE_WQ) cast += 'Q';
    if (this.castling & CASTLE_BK) cast += 'k';
    if (this.castling & CASTLE_BQ) cast += 'q';
    out += (cast || '-') + ' ';
    out += (this.ep >= 0 ? squareName(this.ep) : '-') + ' ';
    out += this.halfmove + ' ' + this.fullmove;
    return out;
  };

  /* ------------------------------------------------------------------ *
   * Attack detection
   * ------------------------------------------------------------------ */

  Position.prototype.isSquareAttacked = function (sq, byColor) {
    var b = this.board, i, t, off, s, pc;

    // Pawns. A white pawn on s attacks s+15 and s+17, so an attacker of sq
    // sits on sq-15 / sq-17.
    if (byColor === WHITE) {
      var wp = pieceOf(WHITE, PAWN);
      s = sq - 15; if (onBoard(s) && b[s] === wp) return true;
      s = sq - 17; if (onBoard(s) && b[s] === wp) return true;
    } else {
      var bp = pieceOf(BLACK, PAWN);
      s = sq + 15; if (onBoard(s) && b[s] === bp) return true;
      s = sq + 17; if (onBoard(s) && b[s] === bp) return true;
    }

    var kn = pieceOf(byColor, KNIGHT);
    for (i = 0; i < 8; i++) {
      s = sq + KNIGHT_OFFS[i];
      if (onBoard(s) && b[s] === kn) return true;
    }

    var kg = pieceOf(byColor, KING);
    for (i = 0; i < 8; i++) {
      s = sq + KING_OFFS[i];
      if (onBoard(s) && b[s] === kg) return true;
    }

    var rk = pieceOf(byColor, ROOK), qn = pieceOf(byColor, QUEEN);
    for (i = 0; i < 4; i++) {
      off = ROOK_OFFS[i];
      for (s = sq + off; onBoard(s); s += off) {
        pc = b[s];
        if (pc !== 0) {
          if (pc === rk || pc === qn) return true;
          break;
        }
      }
    }

    var bi = pieceOf(byColor, BISHOP);
    for (i = 0; i < 4; i++) {
      off = BISHOP_OFFS[i];
      for (s = sq + off; onBoard(s); s += off) {
        pc = b[s];
        if (pc !== 0) {
          if (pc === bi || pc === qn) return true;
          break;
        }
      }
    }
    return false;
  };

  Position.prototype.inCheck = function (color) {
    if (color === undefined) color = this.side;
    var k = this.kingSq[color];
    if (k < 0) return false;
    return this.isSquareAttacked(k, color ^ 1);
  };

  /* ------------------------------------------------------------------ *
   * Move generation (pseudo-legal; legality is filtered in makeMove)
   * ------------------------------------------------------------------ */

  Position.prototype.generateMoves = function (capturesOnly, out) {
    var moves = out || [];
    var us = this.side, them = us ^ 1;
    var b = this.board;
    var list = this.plist[us], n = this.pcount[us];
    var i, j, from, to, off, pc, target;

    for (i = 0; i < n; i++) {
      from = list[i];
      pc = b[from];
      var t = typeOf(pc);

      if (t === PAWN) {
        var push = us === WHITE ? 16 : -16;
        var promoRank = us === WHITE ? 7 : 0;
        var startRank = us === WHITE ? 1 : 6;

        // Captures, including promotion-captures.
        for (j = -1; j <= 1; j += 2) {
          to = from + push + j;
          if (!onBoard(to)) continue;
          target = b[to];
          if (target !== 0 && colorOf(target) === them) {
            if (rankOf(to) === promoRank) {
              moves.push(encodeMove(from, to, target, QUEEN, 0));
              moves.push(encodeMove(from, to, target, ROOK, 0));
              moves.push(encodeMove(from, to, target, BISHOP, 0));
              moves.push(encodeMove(from, to, target, KNIGHT, 0));
            } else {
              moves.push(encodeMove(from, to, target, 0, 0));
            }
          } else if (target === 0 && to === this.ep) {
            moves.push(encodeMove(from, to, pieceOf(them, PAWN), 0, FLAG_EP));
          }
        }

        // Quiet pushes. Promotions count as captures for quiescence search
        // because they change material, so they are generated either way.
        to = from + push;
        if (onBoard(to) && b[to] === 0) {
          if (rankOf(to) === promoRank) {
            moves.push(encodeMove(from, to, 0, QUEEN, 0));
            if (!capturesOnly) {
              moves.push(encodeMove(from, to, 0, ROOK, 0));
              moves.push(encodeMove(from, to, 0, BISHOP, 0));
              moves.push(encodeMove(from, to, 0, KNIGHT, 0));
            }
          } else if (!capturesOnly) {
            moves.push(encodeMove(from, to, 0, 0, 0));
            if (rankOf(from) === startRank) {
              var to2 = to + push;
              if (b[to2] === 0) moves.push(encodeMove(from, to2, 0, 0, FLAG_PAWN2));
            }
          }
        }
        continue;
      }

      if (t === KNIGHT || t === KING) {
        var offs = t === KNIGHT ? KNIGHT_OFFS : KING_OFFS;
        for (j = 0; j < 8; j++) {
          to = from + offs[j];
          if (!onBoard(to)) continue;
          target = b[to];
          if (target === 0) {
            if (!capturesOnly) moves.push(encodeMove(from, to, 0, 0, 0));
          } else if (colorOf(target) === them) {
            moves.push(encodeMove(from, to, target, 0, 0));
          }
        }
        continue;
      }

      // Sliding pieces.
      var dirs, ndirs;
      if (t === BISHOP) { dirs = BISHOP_OFFS; ndirs = 4; }
      else if (t === ROOK) { dirs = ROOK_OFFS; ndirs = 4; }
      else { dirs = KING_OFFS; ndirs = 8; }
      for (j = 0; j < ndirs; j++) {
        off = dirs[j];
        for (to = from + off; onBoard(to); to += off) {
          target = b[to];
          if (target === 0) {
            if (!capturesOnly) moves.push(encodeMove(from, to, 0, 0, 0));
            continue;
          }
          if (colorOf(target) === them) moves.push(encodeMove(from, to, target, 0, 0));
          break;
        }
      }
    }

    if (!capturesOnly) this.generateCastles(moves);
    return moves;
  };

  Position.prototype.generateCastles = function (moves) {
    var us = this.side, them = us ^ 1, b = this.board;
    if (us === WHITE) {
      if ((this.castling & CASTLE_WK) && b[5] === 0 && b[6] === 0 &&
          !this.isSquareAttacked(4, them) && !this.isSquareAttacked(5, them) &&
          !this.isSquareAttacked(6, them)) {
        moves.push(encodeMove(4, 6, 0, 0, FLAG_CASTLE));
      }
      if ((this.castling & CASTLE_WQ) && b[3] === 0 && b[2] === 0 && b[1] === 0 &&
          !this.isSquareAttacked(4, them) && !this.isSquareAttacked(3, them) &&
          !this.isSquareAttacked(2, them)) {
        moves.push(encodeMove(4, 2, 0, 0, FLAG_CASTLE));
      }
    } else {
      if ((this.castling & CASTLE_BK) && b[117] === 0 && b[118] === 0 &&
          !this.isSquareAttacked(116, them) && !this.isSquareAttacked(117, them) &&
          !this.isSquareAttacked(118, them)) {
        moves.push(encodeMove(116, 118, 0, 0, FLAG_CASTLE));
      }
      if ((this.castling & CASTLE_BQ) && b[115] === 0 && b[114] === 0 && b[113] === 0 &&
          !this.isSquareAttacked(116, them) && !this.isSquareAttacked(115, them) &&
          !this.isSquareAttacked(114, them)) {
        moves.push(encodeMove(116, 114, 0, 0, FLAG_CASTLE));
      }
    }
  };

  /* ------------------------------------------------------------------ *
   * Make / unmake
   * ------------------------------------------------------------------ */

  // Returns false and leaves the position untouched if the move would leave
  // our own king in check.
  Position.prototype.makeMove = function (move) {
    var us = this.side, them = us ^ 1;
    var from = moveFrom(move), to = moveTo(move);
    var flags = moveFlags(move), promo = movePromo(move);
    var captured = moveCaptured(move);

    this.undoStack.push({
      move: move,
      castling: this.castling,
      ep: this.ep,
      halfmove: this.halfmove,
      fullmove: this.fullmove,
      hlo: this.hlo,
      hhi: this.hhi
    });

    // Roll the incremental hash out of the old castling/ep state; the new
    // state is folded back in at the end.
    this.hlo ^= ZCASTLE_LO[this.castling]; this.hhi ^= ZCASTLE_HI[this.castling];
    if (this.ep >= 0) { this.hlo ^= ZEP_LO[fileOf(this.ep)]; this.hhi ^= ZEP_HI[fileOf(this.ep)]; }

    var movingPiece = this.board[from];
    var isPawn = typeOf(movingPiece) === PAWN;

    if (flags & FLAG_EP) {
      this.removePiece(us === WHITE ? to - 16 : to + 16);
    } else if (captured) {
      this.removePiece(to);
    }

    this.movePiece(from, to);

    if (promo) {
      this.removePiece(to);
      this.addPiece(to, pieceOf(us, promo));
    }

    if (flags & FLAG_CASTLE) {
      if (to === 6) this.movePiece(7, 5);
      else if (to === 2) this.movePiece(0, 3);
      else if (to === 118) this.movePiece(119, 117);
      else if (to === 114) this.movePiece(112, 115);
    }

    this.castling &= CASTLE_MASK[from] & CASTLE_MASK[to];
    this.ep = (flags & FLAG_PAWN2) ? (us === WHITE ? from + 16 : from - 16) : -1;
    this.halfmove = (isPawn || captured) ? 0 : this.halfmove + 1;
    if (us === BLACK) this.fullmove++;
    this.side = them;

    this.hlo ^= ZCASTLE_LO[this.castling]; this.hhi ^= ZCASTLE_HI[this.castling];
    if (this.ep >= 0) { this.hlo ^= ZEP_LO[fileOf(this.ep)]; this.hhi ^= ZEP_HI[fileOf(this.ep)]; }
    this.hlo ^= ZSIDE_LO; this.hhi ^= ZSIDE_HI;

    if (this.isSquareAttacked(this.kingSq[us], them)) {
      this.unmakeMove();
      return false;
    }
    this.repHist.push(this.hlo);
    return true;
  };

  Position.prototype.unmakeMove = function () {
    var undo = this.undoStack.pop();
    if (!undo) return;
    var move = undo.move;
    var from = moveFrom(move), to = moveTo(move);
    var flags = moveFlags(move), promo = movePromo(move);
    var captured = moveCaptured(move);
    var us = this.side ^ 1;

    if (flags & FLAG_CASTLE) {
      if (to === 6) this.movePiece(5, 7);
      else if (to === 2) this.movePiece(3, 0);
      else if (to === 118) this.movePiece(117, 119);
      else if (to === 114) this.movePiece(115, 112);
    }

    if (promo) {
      this.removePiece(to);
      this.addPiece(to, pieceOf(us, PAWN));
    }

    this.movePiece(to, from);

    if (flags & FLAG_EP) {
      this.addPiece(us === WHITE ? to - 16 : to + 16, pieceOf(us ^ 1, PAWN));
    } else if (captured) {
      this.addPiece(to, captured);
    }

    this.side = us;
    this.castling = undo.castling;
    this.ep = undo.ep;
    this.halfmove = undo.halfmove;
    this.fullmove = undo.fullmove;
    this.hlo = undo.hlo;
    this.hhi = undo.hhi;
    this.repHist.pop();
  };

  // Applies a move without the legality retraction, used only after the move
  // is already known to be legal (it still returns the same boolean).
  Position.prototype.legalMoves = function () {
    var pseudo = this.generateMoves(false);
    var legal = [];
    for (var i = 0; i < pseudo.length; i++) {
      if (this.makeMove(pseudo[i])) {
        legal.push(pseudo[i]);
        this.unmakeMove();
      }
    }
    return legal;
  };

  /* ------------------------------------------------------------------ *
   * Game state
   * ------------------------------------------------------------------ */

  Position.prototype.isRepetition = function () {
    var n = this.repHist.length;
    var limit = Math.min(this.halfmove, n - 1);
    var key = this.repHist[n - 1];
    // Only positions with the same side to move can repeat, hence the step 2.
    for (var i = 4; i <= limit; i += 2) {
      if (this.repHist[n - 1 - i] === key) return true;
    }
    return false;
  };

  Position.prototype.hasInsufficientMaterial = function () {
    var bishops = [[], []];
    var knights = [0, 0];
    for (var c = 0; c < 2; c++) {
      var list = this.plist[c], n = this.pcount[c];
      for (var i = 0; i < n; i++) {
        var t = typeOf(this.board[list[i]]);
        if (t === PAWN || t === ROOK || t === QUEEN) return false;
        if (t === KNIGHT) knights[c]++;
        else if (t === BISHOP) bishops[c].push((fileOf(list[i]) + rankOf(list[i])) & 1);
      }
    }
    var wb = bishops[0].length, bb = bishops[1].length;
    var total = wb + bb + knights[0] + knights[1];
    if (total <= 1) return true;                       // K v K, K+minor v K
    if (knights[0] + knights[1] === 0) {
      // Any number of bishops, all on one colour complex, cannot mate.
      var all = bishops[0].concat(bishops[1]);
      var first = all[0];
      for (var j = 1; j < all.length; j++) if (all[j] !== first) return false;
      return true;
    }
    return false;
  };

  // 'checkmate' | 'stalemate' | 'fifty-move' | 'repetition' |
  // 'insufficient-material' | null
  Position.prototype.gameResult = function () {
    if (this.legalMoves().length === 0) {
      return this.inCheck() ? 'checkmate' : 'stalemate';
    }
    if (this.halfmove >= 100) return 'fifty-move';
    if (this.hasInsufficientMaterial()) return 'insufficient-material';
    if (this.isRepetition()) return 'repetition';
    return null;
  };

  /* ------------------------------------------------------------------ *
   * SAN
   * ------------------------------------------------------------------ */

  Position.prototype.moveToSan = function (move) {
    var from = moveFrom(move), to = moveTo(move);
    var flags = moveFlags(move), promo = movePromo(move);
    var pc = this.board[from];
    var t = typeOf(pc);
    var san;

    if (flags & FLAG_CASTLE) {
      san = (fileOf(to) === 6) ? 'O-O' : 'O-O-O';
    } else {
      var isCapture = moveCaptured(move) !== 0;
      if (t === PAWN) {
        san = isCapture ? 'abcdefgh'.charAt(fileOf(from)) + 'x' : '';
        san += squareName(to);
        if (promo) san += '=' + PIECE_TO_CHAR[pieceOf(WHITE, promo)];
      } else {
        san = PIECE_TO_CHAR[pieceOf(WHITE, t)];
        // Disambiguate against other same-type pieces reaching the same square.
        var others = [];
        var legal = this.legalMoves();
        for (var i = 0; i < legal.length; i++) {
          var m = legal[i];
          if (m === move) continue;
          if (moveTo(m) !== to) continue;
          if (typeOf(this.board[moveFrom(m)]) !== t) continue;
          others.push(moveFrom(m));
        }
        if (others.length) {
          var sameFile = false, sameRank = false;
          for (var k = 0; k < others.length; k++) {
            if (fileOf(others[k]) === fileOf(from)) sameFile = true;
            if (rankOf(others[k]) === rankOf(from)) sameRank = true;
          }
          if (!sameFile) san += 'abcdefgh'.charAt(fileOf(from));
          else if (!sameRank) san += (rankOf(from) + 1);
          else san += squareName(from);
        }
        if (isCapture) san += 'x';
        san += squareName(to);
      }
    }

    if (this.makeMove(move)) {
      if (this.inCheck()) san += this.legalMoves().length === 0 ? '#' : '+';
      this.unmakeMove();
    }
    return san;
  };

  // Accepts SAN ("Nf3", "exd8=Q+", "O-O") or UCI ("g1f3", "e7e8q").
  Position.prototype.moveFromString = function (str) {
    if (!str) return 0;
    var legal = this.legalMoves(), i;
    var cleaned = String(str).trim();
    for (i = 0; i < legal.length; i++) {
      if (moveToUci(legal[i]) === cleaned.toLowerCase()) return legal[i];
    }
    var normalized = cleaned.replace(/[+#!?]/g, '').replace(/0/g, 'O');
    for (i = 0; i < legal.length; i++) {
      var san = this.moveToSan(legal[i]).replace(/[+#!?]/g, '');
      if (san === normalized) return legal[i];
    }
    return 0;
  };

  /* ------------------------------------------------------------------ *
   * Evaluation
   *
   * Tapered piece-square evaluation (PeSTO tables) plus a handful of
   * structural terms. Scores are in centipawns from White's point of view
   * and negated for Black before being returned to the search.
   * ------------------------------------------------------------------ */

  var MG_VALUE = [0, 82, 337, 365, 477, 1025, 0];
  var EG_VALUE = [0, 94, 281, 297, 512, 936, 0];
  var PHASE_WEIGHT = [0, 0, 1, 1, 2, 4, 0];

  // Tables are written from White's perspective with index 0 = a8.
  var MG_PAWN = [
      0,   0,   0,   0,   0,   0,   0,   0,
     98, 134,  61,  95,  68, 126,  34, -11,
     -6,   7,  26,  31,  65,  56,  25, -20,
    -14,  13,   6,  21,  23,  12,  17, -23,
    -27,  -2,  -5,  12,  17,   6,  10, -25,
    -26,  -4,  -4, -10,   3,   3,  33, -12,
    -35,  -1, -20, -23, -15,  24,  38, -22,
      0,   0,   0,   0,   0,   0,   0,   0
  ];
  var EG_PAWN = [
      0,   0,   0,   0,   0,   0,   0,   0,
    178, 173, 158, 134, 147, 132, 165, 187,
     94, 100,  85,  67,  56,  53,  82,  84,
     32,  24,  13,   5,  -2,   4,  17,  17,
     13,   9,  -3,  -7,  -7,  -8,   3,  -1,
      4,   7,  -6,   1,   0,  -5,  -1,  -8,
     13,   8,   8,  10,  13,   0,   2,  -7,
      0,   0,   0,   0,   0,   0,   0,   0
  ];
  var MG_KNIGHT = [
   -167, -89, -34, -49,  61, -97, -15, -107,
    -73, -41,  72,  36,  23,  62,   7,  -17,
    -47,  60,  37,  65,  84, 129,  73,   44,
     -9,  17,  19,  53,  37,  69,  18,   22,
    -13,   4,  16,  13,  28,  19,  21,   -8,
    -23,  -9,  12,  10,  19,  17,  25,  -16,
    -29, -53, -12,  -3,  -1,  18, -14,  -19,
   -105, -21, -58, -33, -17, -28, -19,  -23
  ];
  var EG_KNIGHT = [
    -58, -38, -13, -28, -31, -27, -63, -99,
    -25,  -8, -25,  -2,  -9, -25, -24, -52,
    -24, -20,  10,   9,  -1,  -9, -19, -41,
    -17,   3,  22,  22,  22,  11,   8, -18,
    -18,  -6,  16,  25,  16,  17,   4, -18,
    -23,  -3,  -1,  15,  10,  -3, -20, -22,
    -42, -20, -10,  -5,  -2, -20, -23, -44,
    -29, -51, -23, -15, -22, -18, -50, -64
  ];
  var MG_BISHOP = [
    -29,   4, -82, -37, -25, -42,   7,  -8,
    -26,  16, -18, -13,  30,  59,  18, -47,
    -16,  37,  43,  40,  35,  50,  37,  -2,
     -4,   5,  19,  50,  37,  37,   7,  -2,
     -6,  13,  13,  26,  34,  12,  10,   4,
      0,  15,  15,  15,  14,  27,  18,  10,
      4,  15,  16,   0,   7,  21,  33,   1,
    -33,  -3, -14, -21, -13, -12, -39, -21
  ];
  var EG_BISHOP = [
    -14, -21, -11,  -8,  -7,  -9, -17, -24,
     -8,  -4,   7, -12,  -3, -13,  -4, -14,
      2,  -8,   0,  -1,  -2,   6,   0,   4,
     -3,   9,  12,   9,  14,  10,   3,   2,
     -6,   3,  13,  19,   7,  10,  -3,  -9,
    -12,  -3,   8,  10,  13,   3,  -7, -15,
    -14, -18,  -7,  -1,   4,  -9, -15, -27,
    -23,  -9, -23,  -5,  -9, -16,  -5, -17
  ];
  var MG_ROOK = [
     32,  42,  32,  51,  63,   9,  31,  43,
     27,  32,  58,  62,  80,  67,  26,  44,
     -5,  19,  26,  36,  17,  45,  61,  16,
    -24, -11,   7,  26,  24,  35,  -8, -20,
    -36, -26, -12,  -1,   9,  -7,   6, -23,
    -45, -25, -16, -17,   3,   0,  -5, -33,
    -44, -16, -20,  -9,  -1,  11,  -6, -71,
    -19, -13,   1,  17,  16,   7, -37, -26
  ];
  var EG_ROOK = [
     13,  10,  18,  15,  12,  12,   8,   5,
     11,  13,  13,  11,  -3,   3,   8,   3,
      7,   7,   7,   5,   4,  -3,  -5,  -3,
      4,   3,  13,   1,   2,   1,  -1,   2,
      3,   5,   8,   4,  -5,  -6,  -8, -11,
     -4,   0,  -5,  -1,  -7, -12,  -8, -16,
     -6,  -6,   0,   2,  -9,  -9, -11,  -3,
     -9,   2,   3,  -1,  -5, -13,   4, -20
  ];
  var MG_QUEEN = [
    -28,   0,  29,  12,  59,  44,  43,  45,
    -24, -39,  -5,   1, -16,  57,  28,  54,
    -13, -17,   7,   8,  29,  56,  47,  57,
    -27, -27, -16, -16,  -1,  17,  -2,   1,
     -9, -26,  -9, -10,  -2,  -4,   3,  -3,
    -14,   2, -11,  -2,  -5,   2,  14,   5,
    -35,  -8,  11,   2,   8,  15,  -3,   1,
     -1, -18,  -9,  10, -15, -25, -31, -50
  ];
  var EG_QUEEN = [
     -9,  22,  22,  27,  27,  19,  10,  20,
    -17,  20,  32,  41,  58,  25,  30,   0,
    -20,   6,   9,  49,  47,  35,  19,   9,
      3,  22,  24,  45,  57,  40,  57,  36,
    -18,  28,  19,  47,  31,  34,  39,  23,
    -16, -27,  15,   6,   9,  17,  10,   5,
    -22, -23, -30, -16, -16, -23, -36, -32,
    -33, -28, -22, -43,  -5, -32, -20, -41
  ];
  var MG_KING = [
    -65,  23,  16, -15, -56, -34,   2,  13,
     29,  -1, -20,  -7,  -8,  -4, -38, -29,
     -9,  24,   2, -16, -20,   6,  22, -22,
    -17, -20, -12, -27, -30, -25, -14, -36,
    -49,  -1, -27, -39, -46, -44, -33, -51,
    -14, -14, -22, -46, -44, -30, -15, -27,
      1,   7,  -8, -64, -43, -16,   9,   8,
    -15,  36,  12, -54,   8, -28,  24,  14
  ];
  var EG_KING = [
    -74, -35, -18, -18, -11,  15,   4, -17,
    -12,  17,  14,  17,  17,  38,  23,  11,
     10,  17,  23,  15,  20,  45,  44,  13,
     -8,  22,  24,  27,  26,  33,  26,   3,
    -18,  -4,  21,  24,  27,  23,   9, -11,
    -19,  -3,  11,  21,  23,  16,   7,  -9,
    -27, -11,   4,  13,  14,   4,  -5, -17,
    -53, -34, -21, -11, -28, -14, -24, -43
  ];

  var MG_TABLE = [null, MG_PAWN, MG_KNIGHT, MG_BISHOP, MG_ROOK, MG_QUEEN, MG_KING];
  var EG_TABLE = [null, EG_PAWN, EG_KNIGHT, EG_BISHOP, EG_ROOK, EG_QUEEN, EG_KING];

  // Flattened [type][color][sq64] lookups so eval does no index arithmetic.
  var MG_PSQT = [], EG_PSQT = [];
  for (var pt = 1; pt <= 6; pt++) {
    MG_PSQT[pt] = [new Int32Array(64), new Int32Array(64)];
    EG_PSQT[pt] = [new Int32Array(64), new Int32Array(64)];
    for (var s64 = 0; s64 < 64; s64++) {
      // White reads the table mirrored (a1 is the bottom-left of the board,
      // the table starts at a8); Black reads it directly.
      MG_PSQT[pt][WHITE][s64] = MG_VALUE[pt] + MG_TABLE[pt][s64 ^ 56];
      EG_PSQT[pt][WHITE][s64] = EG_VALUE[pt] + EG_TABLE[pt][s64 ^ 56];
      MG_PSQT[pt][BLACK][s64] = MG_VALUE[pt] + MG_TABLE[pt][s64];
      EG_PSQT[pt][BLACK][s64] = EG_VALUE[pt] + EG_TABLE[pt][s64];
    }
  }

  var PASSED_BONUS = [0, 10, 17, 15, 32, 71, 136, 0];
  var ISOLATED_PENALTY = 14;
  var DOUBLED_PENALTY = 12;
  var BISHOP_PAIR = 32;
  var ROOK_OPEN_FILE = 22;
  var ROOK_SEMI_OPEN_FILE = 10;
  var TEMPO = 12;

  var pawnCountFile = [new Int32Array(8), new Int32Array(8)];
  var pawnMaxRank = new Int32Array(8);   // most advanced black pawn per file
  var pawnMinRank = new Int32Array(8);   // most advanced white pawn per file

  Position.prototype.evaluate = function () {
    var mg = 0, eg = 0, phase = 0;
    var c, i, list, n, sq, pc, t, s64, f, r;

    pawnCountFile[0].fill(0);
    pawnCountFile[1].fill(0);
    pawnMaxRank.fill(-1);
    pawnMinRank.fill(8);

    for (c = 0; c < 2; c++) {
      list = this.plist[c]; n = this.pcount[c];
      for (i = 0; i < n; i++) {
        sq = list[i];
        if (typeOf(this.board[sq]) !== PAWN) continue;
        f = fileOf(sq); r = rankOf(sq);
        pawnCountFile[c][f]++;
        if (c === WHITE) { if (r < pawnMinRank[f]) pawnMinRank[f] = r; }
        else { if (r > pawnMaxRank[f]) pawnMaxRank[f] = r; }
      }
    }

    for (c = 0; c < 2; c++) {
      var sign = c === WHITE ? 1 : -1;
      var bishops = 0;
      list = this.plist[c]; n = this.pcount[c];
      for (i = 0; i < n; i++) {
        sq = list[i];
        pc = this.board[sq];
        t = typeOf(pc);
        s64 = to64(sq);
        mg += sign * MG_PSQT[t][c][s64];
        eg += sign * EG_PSQT[t][c][s64];
        phase += PHASE_WEIGHT[t];
        f = fileOf(sq); r = rankOf(sq);

        if (t === PAWN) {
          if (pawnCountFile[c][f] > 1) { mg -= sign * DOUBLED_PENALTY; eg -= sign * DOUBLED_PENALTY; }
          var hasNeighbour = (f > 0 && pawnCountFile[c][f - 1] > 0) ||
                             (f < 7 && pawnCountFile[c][f + 1] > 0);
          if (!hasNeighbour) { mg -= sign * ISOLATED_PENALTY; eg -= sign * ISOLATED_PENALTY; }

          var passed = true;
          for (var df = -1; df <= 1; df++) {
            var g = f + df;
            if (g < 0 || g > 7) continue;
            if (c === WHITE) { if (pawnMaxRank[g] > r) { passed = false; break; } }
            else { if (pawnMinRank[g] < r) { passed = false; break; } }
          }
          if (passed) {
            var rel = c === WHITE ? r : 7 - r;
            mg += sign * (PASSED_BONUS[rel] >> 1);
            eg += sign * PASSED_BONUS[rel];
          }
        } else if (t === BISHOP) {
          bishops++;
        } else if (t === ROOK) {
          if (pawnCountFile[c][f] === 0) {
            if (pawnCountFile[c ^ 1][f] === 0) { mg += sign * ROOK_OPEN_FILE; eg += sign * (ROOK_OPEN_FILE >> 1); }
            else { mg += sign * ROOK_SEMI_OPEN_FILE; eg += sign * (ROOK_SEMI_OPEN_FILE >> 1); }
          }
        }
      }
      if (bishops >= 2) { mg += sign * BISHOP_PAIR; eg += sign * (BISHOP_PAIR + 12); }
    }

    if (phase > 24) phase = 24;
    var score = ((mg * phase) + (eg * (24 - phase))) / 24 | 0;
    score += this.side === WHITE ? TEMPO : -TEMPO;
    return this.side === WHITE ? score : -score;
  };

  Position.prototype.hasNonPawnMaterial = function (color) {
    var list = this.plist[color], n = this.pcount[color];
    for (var i = 0; i < n; i++) {
      var t = typeOf(this.board[list[i]]);
      if (t >= KNIGHT && t <= QUEEN) return true;
    }
    return false;
  };

  /* ------------------------------------------------------------------ *
   * Search
   * ------------------------------------------------------------------ */

  var MATE = 30000;
  var MATE_BOUND = MATE - 1000;   // scores above this are forced mates
  var INFINITY = 31000;
  var MAX_PLY = 96;

  var TT_EXACT = 1, TT_ALPHA = 2, TT_BETA = 3;

  function Searcher(ttBits) {
    this.ttBits = ttBits || 20;
    this.ttSize = 1 << this.ttBits;
    this.ttMask = this.ttSize - 1;
    this.ttKey = new Int32Array(this.ttSize);
    this.ttMove = new Int32Array(this.ttSize);
    this.ttScore = new Int32Array(this.ttSize);
    this.ttDepth = new Int8Array(this.ttSize);
    this.ttFlag = new Int8Array(this.ttSize);
    this.ttGen = new Int32Array(this.ttSize);
    this.generation = 0;
    this.killers = new Int32Array(MAX_PLY * 2);
    this.history = new Int32Array(15 * 128);
    this.nodes = 0;
    this.stopped = false;
    this.deadline = 0;
    this.pvTable = new Int32Array(MAX_PLY * MAX_PLY);
    this.pvLength = new Int32Array(MAX_PLY);
  }

  Searcher.prototype.clearTable = function () {
    this.ttKey.fill(0);
    this.ttMove.fill(0);
    this.ttDepth.fill(0);
    this.ttFlag.fill(0);
    this.ttGen.fill(0);
    this.generation = 0;
  };

  Searcher.prototype.ttStore = function (pos, depth, score, flag, move, ply) {
    var idx = (pos.hlo & this.ttMask) >>> 0;
    // Prefer deeper entries, but always let a new generation evict an old one.
    if (this.ttKey[idx] === pos.hhi && this.ttDepth[idx] > depth &&
        this.ttGen[idx] === this.generation) return;
    if (score > MATE_BOUND) score += ply;
    else if (score < -MATE_BOUND) score -= ply;
    this.ttKey[idx] = pos.hhi;
    this.ttMove[idx] = move;
    this.ttScore[idx] = score;
    this.ttDepth[idx] = depth;
    this.ttFlag[idx] = flag;
    this.ttGen[idx] = this.generation;
  };

  Searcher.prototype.ttProbe = function (pos) {
    var idx = (pos.hlo & this.ttMask) >>> 0;
    if (this.ttKey[idx] !== pos.hhi) return -1;
    return idx;
  };

  var MVV_LVA_VICTIM = [0, 100, 320, 330, 500, 900, 0];

  Searcher.prototype.scoreMoves = function (pos, moves, scores, ttMove, ply) {
    var k1 = this.killers[ply * 2], k2 = this.killers[ply * 2 + 1];
    for (var i = 0; i < moves.length; i++) {
      var m = moves[i];
      if (m === ttMove) { scores[i] = 2000000; continue; }
      var cap = moveCaptured(m), promo = movePromo(m);
      if (promo === QUEEN) { scores[i] = 1800000 + MVV_LVA_VICTIM[typeOf(cap)]; continue; }
      if (cap) {
        var victim = MVV_LVA_VICTIM[typeOf(cap)];
        var attacker = MVV_LVA_VICTIM[typeOf(pos.board[moveFrom(m)])];
        scores[i] = 1000000 + victim * 16 - attacker;
        continue;
      }
      if (m === k1) { scores[i] = 900000; continue; }
      if (m === k2) { scores[i] = 890000; continue; }
      scores[i] = this.history[pos.board[moveFrom(m)] * 128 + moveTo(m)];
    }
  };

  // Selection sort one move at a time: with good ordering most nodes cut off
  // after the first few moves, so fully sorting the list would be wasted work.
  function pickMove(moves, scores, index) {
    var best = index;
    for (var i = index + 1; i < moves.length; i++) {
      if (scores[i] > scores[best]) best = i;
    }
    if (best !== index) {
      var tm = moves[index]; moves[index] = moves[best]; moves[best] = tm;
      var ts = scores[index]; scores[index] = scores[best]; scores[best] = ts;
    }
    return moves[index];
  }

  Searcher.prototype.checkTime = function () {
    if ((this.nodes & 2047) === 0 && this.deadline && Date.now() >= this.deadline) {
      this.stopped = true;
    }
  };

  Searcher.prototype.quiescence = function (pos, alpha, beta, ply) {
    this.nodes++;
    this.checkTime();
    if (this.stopped) return 0;
    if (ply >= MAX_PLY - 1) return pos.evaluate();

    var standPat = pos.evaluate();
    if (standPat >= beta) return standPat;
    if (standPat > alpha) alpha = standPat;

    var moves = pos.generateMoves(true);
    var scores = new Int32Array(moves.length);
    this.scoreMoves(pos, moves, scores, 0, ply);

    var best = standPat;
    for (var i = 0; i < moves.length; i++) {
      var m = pickMove(moves, scores, i);

      // Delta pruning: if even winning this piece for free cannot reach
      // alpha, the capture is not worth searching.
      var cap = moveCaptured(m);
      if (cap && !movePromo(m)) {
        if (standPat + MVV_LVA_VICTIM[typeOf(cap)] + 200 < alpha) continue;
      }

      if (!pos.makeMove(m)) continue;
      var score = -this.quiescence(pos, -beta, -alpha, ply + 1);
      pos.unmakeMove();
      if (this.stopped) return 0;

      if (score > best) {
        best = score;
        if (score > alpha) alpha = score;
        if (alpha >= beta) break;
      }
    }
    return best;
  };

  Searcher.prototype.negamax = function (pos, depth, alpha, beta, ply, canNull) {
    this.pvLength[ply] = ply;
    this.checkTime();
    if (this.stopped) return 0;

    var isRoot = ply === 0;
    var inCheck = pos.inCheck();

    if (!isRoot) {
      if (pos.halfmove >= 100 || pos.isRepetition() || pos.hasInsufficientMaterial()) return 0;
      if (ply >= MAX_PLY - 1) return pos.evaluate();

      // Mate-distance pruning: never look for a mate longer than one already
      // proven on this path.
      var mateAlpha = alpha > -MATE + ply ? alpha : -MATE + ply;
      var mateBeta = beta < MATE - ply - 1 ? beta : MATE - ply - 1;
      if (mateAlpha >= mateBeta) return mateAlpha;
      alpha = mateAlpha; beta = mateBeta;
    }

    if (inCheck) depth++;                          // check extension
    if (depth <= 0) return this.quiescence(pos, alpha, beta, ply);

    this.nodes++;

    var isPv = beta - alpha > 1;
    var ttMove = 0;
    var idx = this.ttProbe(pos);
    if (idx >= 0) {
      ttMove = this.ttMove[idx];
      if (!isPv && !isRoot && this.ttDepth[idx] >= depth) {
        var ttScore = this.ttScore[idx];
        if (ttScore > MATE_BOUND) ttScore -= ply;
        else if (ttScore < -MATE_BOUND) ttScore += ply;
        var flag = this.ttFlag[idx];
        if (flag === TT_EXACT) return ttScore;
        if (flag === TT_ALPHA && ttScore <= alpha) return ttScore;
        if (flag === TT_BETA && ttScore >= beta) return ttScore;
      }
    }

    var staticEval = inCheck ? -INFINITY : pos.evaluate();

    if (!isPv && !inCheck && !isRoot) {
      // Reverse futility pruning.
      if (depth <= 6 && staticEval - 85 * depth >= beta && Math.abs(beta) < MATE_BOUND) {
        return staticEval;
      }
      // Null-move pruning. Skipped in pawn-only endings, where zugzwang makes
      // "passing" wildly misleading.
      if (canNull && depth >= 3 && staticEval >= beta && pos.hasNonPawnMaterial(pos.side)) {
        var R = 2 + (depth > 6 ? 1 : 0);
        this.makeNullMove(pos);
        var nullScore = -this.negamax(pos, depth - 1 - R, -beta, -beta + 1, ply + 1, false);
        this.unmakeNullMove(pos);
        if (this.stopped) return 0;
        if (nullScore >= beta && Math.abs(nullScore) < MATE_BOUND) return beta;
      }
    }

    var moves = pos.generateMoves(false);
    var scores = new Int32Array(moves.length);
    this.scoreMoves(pos, moves, scores, ttMove, ply);

    var bestScore = -INFINITY;
    var bestMove = 0;
    var legalCount = 0;
    var origAlpha = alpha;
    var futile = !isPv && !inCheck && depth <= 3 &&
                 staticEval + 120 * depth + 150 <= alpha && Math.abs(alpha) < MATE_BOUND;

    for (var i = 0; i < moves.length; i++) {
      var m = pickMove(moves, scores, i);
      var isQuiet = !moveCaptured(m) && !movePromo(m);

      if (futile && legalCount > 0 && isQuiet) continue;

      if (!pos.makeMove(m)) continue;
      legalCount++;

      var givesCheck = pos.inCheck();
      var score;

      if (legalCount === 1) {
        score = -this.negamax(pos, depth - 1, -beta, -alpha, ply + 1, true);
      } else {
        // Late move reductions for quiet moves late in the ordering.
        var reduction = 0;
        if (depth >= 3 && legalCount > 3 && isQuiet && !givesCheck) {
          reduction = 1 + ((depth > 6 && legalCount > 6) ? 1 : 0);
          if (isPv && reduction > 1) reduction--;
        }
        score = -this.negamax(pos, depth - 1 - reduction, -alpha - 1, -alpha, ply + 1, true);
        if (!this.stopped && reduction > 0 && score > alpha) {
          score = -this.negamax(pos, depth - 1, -alpha - 1, -alpha, ply + 1, true);
        }
        if (!this.stopped && score > alpha && score < beta) {
          score = -this.negamax(pos, depth - 1, -beta, -alpha, ply + 1, true);
        }
      }

      pos.unmakeMove();
      if (this.stopped) return 0;

      if (score > bestScore) {
        bestScore = score;
        bestMove = m;
        if (score > alpha) {
          alpha = score;
          this.updatePv(ply, m);
          if (alpha >= beta) {
            if (isQuiet) {
              var kbase = ply * 2;
              if (this.killers[kbase] !== m) {
                this.killers[kbase + 1] = this.killers[kbase];
                this.killers[kbase] = m;
              }
              this.history[pos.board[moveFrom(m)] * 128 + moveTo(m)] += depth * depth;
            }
            this.ttStore(pos, depth, bestScore, TT_BETA, m, ply);
            return bestScore;
          }
        }
      }
    }

    if (legalCount === 0) {
      // No legal move: mate scores are relative to the current ply so that
      // shorter mates score higher.
      return inCheck ? -MATE + ply : 0;
    }

    this.ttStore(pos, depth, bestScore,
      bestScore > origAlpha ? TT_EXACT : TT_ALPHA, bestMove, ply);
    return bestScore;
  };

  Searcher.prototype.updatePv = function (ply, move) {
    var base = ply * MAX_PLY;
    this.pvTable[base + ply] = move;
    var childBase = (ply + 1) * MAX_PLY;
    for (var i = ply + 1; i < this.pvLength[ply + 1]; i++) {
      this.pvTable[base + i] = this.pvTable[childBase + i];
    }
    this.pvLength[ply] = this.pvLength[ply + 1];
  };

  Searcher.prototype.makeNullMove = function (pos) {
    pos.undoStack.push({
      move: 0, castling: pos.castling, ep: pos.ep,
      halfmove: pos.halfmove, fullmove: pos.fullmove,
      hlo: pos.hlo, hhi: pos.hhi, isNull: true
    });
    if (pos.ep >= 0) {
      pos.hlo ^= ZEP_LO[fileOf(pos.ep)];
      pos.hhi ^= ZEP_HI[fileOf(pos.ep)];
      pos.ep = -1;
    }
    pos.side ^= 1;
    pos.hlo ^= ZSIDE_LO; pos.hhi ^= ZSIDE_HI;
    pos.halfmove++;
    pos.repHist.push(pos.hlo);
  };

  Searcher.prototype.unmakeNullMove = function (pos) {
    var undo = pos.undoStack.pop();
    pos.side ^= 1;
    pos.castling = undo.castling;
    pos.ep = undo.ep;
    pos.halfmove = undo.halfmove;
    pos.fullmove = undo.fullmove;
    pos.hlo = undo.hlo;
    pos.hhi = undo.hhi;
    pos.repHist.pop();
  };

  /**
   * Iterative-deepening search.
   *
   * opts: { maxDepth, timeMs, onIteration(info) }
   * Returns { move, san, uci, score, mate, depth, nodes, timeMs, pv, pvSan }.
   */
  Searcher.prototype.search = function (pos, opts) {
    opts = opts || {};
    var maxDepth = Math.min(opts.maxDepth || 64, MAX_PLY - 2);
    var timeMs = opts.timeMs || 2000;
    var start = Date.now();

    this.nodes = 0;
    this.stopped = false;
    this.deadline = start + timeMs;
    this.generation++;
    this.killers.fill(0);
    // Decay rather than clear: ordering knowledge from the previous search is
    // still useful, just less trustworthy.
    for (var h = 0; h < this.history.length; h++) this.history[h] >>= 1;

    var legal = pos.legalMoves();
    if (legal.length === 0) {
      return { move: 0, san: null, uci: null, score: pos.inCheck() ? -MATE : 0,
               mate: pos.inCheck() ? 0 : null, depth: 0, nodes: 0,
               timeMs: 0, pv: [], pvSan: [] };
    }

    var best = { move: legal[0], score: 0, depth: 0, pv: [legal[0]] };
    var alpha = -INFINITY, beta = INFINITY;

    for (var depth = 1; depth <= maxDepth; depth++) {
      var score = this.negamax(pos, depth, alpha, beta, 0, true);

      // Aspiration window: re-search with a full window if the score fell
      // outside the narrow one.
      if (!this.stopped && (score <= alpha || score >= beta)) {
        alpha = -INFINITY; beta = INFINITY;
        score = this.negamax(pos, depth, alpha, beta, 0, true);
      }
      if (this.stopped) break;

      var pv = [];
      for (var i = 0; i < this.pvLength[0]; i++) pv.push(this.pvTable[i]);
      if (!pv.length) pv = [best.move];

      best = { move: pv[0], score: score, depth: depth, pv: pv };

      if (opts.onIteration) {
        opts.onIteration(this.describe(pos, best, Date.now() - start));
      }

      // A forced mate is proven; searching deeper cannot improve on it.
      if (Math.abs(score) > MATE_BOUND) break;
      if (Date.now() >= this.deadline) break;

      alpha = score - 40;
      beta = score + 40;
    }

    return this.describe(pos, best, Date.now() - start);
  };

  Searcher.prototype.describe = function (pos, best, elapsed) {
    var pvSan = [];
    var made = 0, i;
    for (i = 0; i < best.pv.length; i++) {
      var m = best.pv[i];
      // The stored PV can go stale after a fail-high; stop at the first move
      // that is no longer legal rather than reporting nonsense.
      var san;
      try { san = pos.moveToSan(m); } catch (e) { break; }
      if (!pos.makeMove(m)) break;
      made++;
      pvSan.push(san);
    }
    for (i = 0; i < made; i++) pos.unmakeMove();

    var mate = null;
    if (Math.abs(best.score) > MATE_BOUND) {
      var plies = MATE - Math.abs(best.score);
      mate = Math.ceil(plies / 2) * (best.score > 0 ? 1 : -1);
    }

    return {
      move: best.move,
      uci: best.move ? moveToUci(best.move) : null,
      san: pvSan.length ? pvSan[0] : (best.move ? pos.moveToSan(best.move) : null),
      score: best.score,
      mate: mate,
      depth: best.depth,
      nodes: this.nodes,
      timeMs: elapsed,
      pv: best.pv.slice(0, made || best.pv.length),
      pvSan: pvSan
    };
  };

  /* ------------------------------------------------------------------ *
   * Perft (move generation self-test)
   * ------------------------------------------------------------------ */

  function perft(pos, depth) {
    if (depth === 0) return 1;
    var moves = pos.generateMoves(false);
    var total = 0;
    for (var i = 0; i < moves.length; i++) {
      if (!pos.makeMove(moves[i])) continue;
      total += depth === 1 ? 1 : perft(pos, depth - 1);
      pos.unmakeMove();
    }
    return total;
  }

  return {
    Position: Position,
    Searcher: Searcher,
    perft: perft,
    START_FEN: START_FEN,
    WHITE: WHITE, BLACK: BLACK,
    PAWN: PAWN, KNIGHT: KNIGHT, BISHOP: BISHOP, ROOK: ROOK, QUEEN: QUEEN, KING: KING,
    MATE: MATE,
    pieceOf: pieceOf, colorOf: colorOf, typeOf: typeOf,
    PIECE_TO_CHAR: PIECE_TO_CHAR, CHAR_TO_PIECE: CHAR_TO_PIECE,
    squareName: squareName, squareFromName: squareFromName,
    moveFrom: moveFrom, moveTo: moveTo, movePromo: movePromo,
    moveCaptured: moveCaptured, moveFlags: moveFlags, moveToUci: moveToUci,
    onBoard: onBoard, fileOf: fileOf, rankOf: rankOf
  };
});
