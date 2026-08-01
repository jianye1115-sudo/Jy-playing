# Chess Helper ♟

A chess analysis board that runs entirely in your browser. Set up a position,
and it tells you the strongest move, the evaluation, and the line it expects to
follow.

No install, no build step, no dependencies, no network access. Double-click
`index.html` and it works.

> **Intended use:** analysing your own games, studying positions, and practising
> against computer opponents. Don't use it during rated games against people —
> that's cheating, and every major chess site bans it.

---

## Using it

Open `tools/chess-helper/index.html` in any modern browser.

The fastest way to analyse a position from another site:

1. Copy the FEN from that site (most have a "copy FEN" or PGN/analysis option).
2. Paste it into the **FEN** box and press **Load FEN**.
3. Read the best move off the panel — it's also drawn as an arrow on the board.

If you can't get a FEN, press **Set up position**, pick pieces from the palette
and click squares to place them. Set the side to move and castling rights, then
press **Done editing**.

You can also just play moves on the board (click-click or drag, mouse or
touch) and let it analyse as you go.

### Controls

| Control | What it does |
| --- | --- |
| **Analyse** | Search the current position (also `Space`) |
| **Play best move** | Make the suggested move on the board |
| **Undo** | Take back the last move (also `←`) |
| **Flip board** | Swap which side is at the bottom (also `f`) |
| **New game** | Reset to the starting position |
| **Set up position** | Open the piece-placement editor |
| **Thinking time** | 0.2s–10s per search. Longer = deeper = stronger |
| **Analyse automatically** | Re-analyse after every move |

### Reading the output

- **Best move** — in standard notation, e.g. `Nf3`, `Qxf7#`, `O-O`.
- **Evaluation** — always from **White's** point of view. `+1.30` means White is
  a pawn and a bit better; `-2.00` means Black is winning by about two pawns.
  `Mate in 3` means a forced mate exists.
- **depth** — how many moves ahead it looked (plus deeper tactical checks).
- The line under the stats is what it expects both sides to play.
- The bar to the left of the board is the same evaluation, drawn.

---

## How strong is it?

Strong enough to beat the bots on chess sites comfortably. At the default 1.5s
it searches to roughly depth 10–13 from a normal middlegame position, which puts
it well above club level. It finds forced mates several moves out and does not
hang pieces.

It is *not* Stockfish. A modern top engine with a neural-network evaluation is
far stronger. This is a hand-written classical engine chosen so the tool stays a
few small files with nothing to install.

Give it more thinking time for sharp or complicated positions — tactics are
where extra depth pays off most.

---

## What's in here

| File | |
| --- | --- |
| `index.html` | The whole interface — board, controls, editor. Inline CSS/JS. |
| `engine.js` | The engine: board, move generation, evaluation, search. |
| `worker.js` | Runs the engine off the main thread when served over http. |
| `test.js` | Self-test suite (`node test.js`). |

### Running the tests

```bash
cd tools/chess-helper
node test.js
```

This runs [perft](https://www.chessprogramming.org/Perft) against seven standard
positions — comparing the exact number of leaf nodes at each depth to published
values, which is what proves move generation, make/unmake and legality filtering
are correct — plus checks on hashing, FEN, notation, draw detection, evaluation
symmetry and the search's ability to find known mates. About 17 million nodes;
takes a few seconds.

---

## Notes on the implementation

- **Board** — 0x88 mailbox, so off-board detection is a single mask. Piece lists
  are kept incrementally so move generation only walks occupied squares.
- **Evaluation** — tapered midgame/endgame piece-square tables
  ([PeSTO](https://www.chessprogramming.org/PeSTO%27s_Evaluation_Function)),
  plus passed/isolated/doubled pawns, the bishop pair, and rooks on open files.
- **Search** — alpha-beta with iterative deepening, principal variation search,
  a Zobrist-keyed transposition table, quiescence search, null-move pruning,
  late move reductions, futility pruning, check extensions and killer/history
  move ordering.
- **Threading** — a Web Worker keeps the interface responsive. Browsers refuse
  to start workers on pages opened directly from disk (`file://`), so in that
  case the page falls back to searching in-page, one depth per task, yielding to
  the browser between depths. Both paths work; the worker just streams updates
  more smoothly. To get the worker, serve the folder:

  ```bash
  cd tools/chess-helper && python3 -m http.server 8000
  # then open http://localhost:8000
  ```

### Known limits

- No opening book — early moves are searched from scratch, so it may pick
  offbeat but sound openings.
- Endgames are evaluated, not tablebase-perfect. It wins won endings but may not
  take the shortest path.
- Positions are validated before analysis (one king per side, no pawns on the
  back rank, side-not-to-move not in check), since impossible positions make the
  search misbehave.
