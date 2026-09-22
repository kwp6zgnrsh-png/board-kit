<template>
  <section class="relative min-h-40 overflow-hidden rounded-lg border-[3px] border-slate-950 bg-[#fff5ee] px-6 py-4 sm:px-8 lg:px-9">
    <div class="relative z-10 max-w-3xl">
      <p class="mb-2 text-[9px] font-black tracking-[0.34em] text-slate-700 sm:text-[10px]">
        GOOD PEOPLE · GREAT CONVERSATIONS
      </p>

      <h1 class="font-display m-0 max-w-3xl text-3xl leading-[0.9] text-slate-950 uppercase italic sm:text-4xl lg:text-5xl">
        WELCOME TO
        <span class="mt-1.5 block w-fit -rotate-1 bg-yellow-300 px-3 py-0.5">
          BOARDKIT
        </span>
      </h1>

      <p class="mt-3 text-sm font-bold tracking-wide text-slate-700 sm:text-base">
        나누고, 묻고, 배우며 함께 만드는 커뮤니티.
      </p>
    </div>

    <div class="pointer-events-none absolute right-4 bottom-0 hidden h-36 w-[40%] min-w-[340px] lg:block" aria-hidden="true">
      <div class="absolute right-52 bottom-[-7px] h-20 w-20 rounded-full border-[3px] border-slate-950 bg-yellow-300">
        <span class="absolute top-7 left-6 h-1.5 w-1.5 rounded-full bg-slate-950"></span>
        <span class="absolute top-7 right-6 h-1.5 w-1.5 rounded-full bg-slate-950"></span>
        <span class="absolute right-6 bottom-5 left-6 h-3 rounded-b-full border-b-[3px] border-slate-950"></span>
      </div>

      <div class="home-burst absolute right-28 bottom-[-7px] h-28 w-28"></div>
      <div class="home-triangle absolute right-0 bottom-[-7px] h-28 w-28"></div>

      <p class="absolute right-16 top-0 m-0 rotate-[-8deg] border-[3px] border-slate-950 bg-slate-950 px-4 py-1.5 text-center text-[10px] font-black tracking-[0.18em] text-white shadow-[2px_2px_0_#fde047]">
        BETTER IDEAS<br />TOGETHER
      </p>
    </div>
  </section>
</template>

<style scoped>
.home-burst,
.home-triangle {
  background: #0f172a;
}

.home-burst,
.home-burst::after {
  clip-path: polygon(
    50% 0%, 61% 23%, 82% 8%, 79% 34%, 100% 39%, 78% 53%,
    94% 73%, 68% 72%, 69% 100%, 51% 79%, 34% 100%, 33% 73%,
    6% 78%, 23% 55%, 0% 42%, 25% 35%, 18% 9%, 40% 24%
  );
}

.home-triangle,
.home-triangle::after {
  clip-path: polygon(50% 0, 100% 100%, 0 100%);
}

.home-burst::after,
.home-triangle::after {
  position: absolute;
  inset: 5px;
  content: '';
}

.home-burst::after {
  background: #3b82f6;
}

.home-triangle::after {
  background: #f472b6;
}
</style>
